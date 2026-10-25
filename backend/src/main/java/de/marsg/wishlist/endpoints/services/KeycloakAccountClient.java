package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * All Keycloak interactions of the backend; Keycloak is the source of truth for account data.
 * Changes go through the Admin API (service account) and our DB copy is written afterwards.
 */
@Component
public class KeycloakAccountClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAccountClient.class);

    private final RestClient restClient;
    private final String realm;
    private final String adminClientId;
    private final String adminClientSecret;
    private final String verifyClientId;

    public KeycloakAccountClient(
            @Value("${wishlist.keycloak.base-url}") String baseUrl,
            @Value("${wishlist.keycloak.realm}") String realm,
            @Value("${wishlist.keycloak.admin-client-id}") String adminClientId,
            @Value("${wishlist.keycloak.admin-client-secret}") String adminClientSecret,
            @Value("${wishlist.keycloak.verify-client-id}") String verifyClientId) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.realm = realm;
        this.adminClientId = adminClientId;
        this.adminClientSecret = adminClientSecret;
        this.verifyClientId = verifyClientId;
    }

    /** Updates email and username via the Admin API and marks the email unverified. */
    public void updateEmail(UUID keycloakId, String newEmail) {
        rewriteUser(keycloakId, user -> {
            user.put("email", newEmail);
            user.put("username", newEmail);
            user.put("emailVerified", false);
        }, "email update");
    }

    /**
     * Mirrors the interface language into the Keycloak {@code locale} attribute so login
     * pages and account emails match the app. The Admin API is used on purpose: the user's
     * own app token has no {@code account} audience, so the Account API answers 401.
     *
     * <p>The update replaces the whole user representation — a body carrying only the
     * attributes drops email and username and is rejected — so the current representation
     * is read first and sent back with just the locale entry overwritten.
     */
    public void updateLocale(UUID keycloakId, String locale) {
        rewriteUser(keycloakId, user -> {
            Map<String, Object> attributes = new HashMap<>();
            if (user.get("attributes") instanceof Map<?, ?> existing) {
                existing.forEach((key, value) -> attributes.put(String.valueOf(key), value));
            }
            attributes.put("locale", List.of(locale));
            user.put("attributes", attributes);
        }, "locale update");
    }

    /** Read-modify-write of the full user representation through the Admin API. */
    private void rewriteUser(UUID keycloakId, Consumer<Map<String, Object>> change, String what) {
        try {
            String token = serviceAccountToken();
            Map<?, ?> current = restClient.get()
                    .uri("/admin/realms/{realm}/users/{id}", realm, keycloakId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(Map.class);
            if (current == null) {
                throw new ApiException(ErrorCode.KEYCLOAK_UPDATE_FAILED);
            }
            Map<String, Object> user = new HashMap<>();
            current.forEach((key, value) -> user.put(String.valueOf(key), value));
            change.accept(user);
            restClient.put()
                    .uri("/admin/realms/{realm}/users/{id}", realm, keycloakId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(user)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Keycloak rejected {} for {}: {}", what, keycloakId, statusOf(e));
            throw new ApiException(ErrorCode.KEYCLOAK_UPDATE_FAILED);
        }
    }

    /** Whether Keycloak still knows the user; 404 is false, any other failure is KEYCLOAK_UNAVAILABLE. */
    public boolean userExists(UUID keycloakId) {
        String token = tokenOrUnavailable(keycloakId);
        try {
            fetchAdmin("/admin/realms/{realm}/users/{id}", keycloakId, token, Map.class);
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            if (isUserNotFound(e)) {
                return false;
            }
            log.warn("Keycloak lookup of {} answered an unexpected 404", keycloakId);
            throw new ApiException(ErrorCode.KEYCLOAK_UNAVAILABLE);
        } catch (RestClientException e) {
            log.warn("Keycloak lookup of {} failed: {}", keycloakId, statusOf(e));
            throw new ApiException(ErrorCode.KEYCLOAK_UNAVAILABLE);
        }
    }

    /** The Keycloak user representation; KEYCLOAK_UNAVAILABLE when it cannot be read. */
    public Map<?, ?> fetchUser(UUID keycloakId) {
        try {
            return fetchAdmin("/admin/realms/{realm}/users/{id}", keycloakId, Map.class);
        } catch (RestClientException e) {
            log.warn("Keycloak user read of {} failed: {}", keycloakId, statusOf(e));
            throw new ApiException(ErrorCode.KEYCLOAK_UNAVAILABLE);
        }
    }

    /** Federated identities of the user; KEYCLOAK_UNAVAILABLE when they cannot be read. */
    public List<?> fetchFederatedIdentities(UUID keycloakId) {
        try {
            return fetchAdmin("/admin/realms/{realm}/users/{id}/federated-identity", keycloakId, List.class);
        } catch (RestClientException e) {
            log.warn("Keycloak identity read of {} failed: {}", keycloakId, statusOf(e));
            throw new ApiException(ErrorCode.KEYCLOAK_UNAVAILABLE);
        }
    }

    /** Service token fetched outside any 404 handling; KEYCLOAK_UNAVAILABLE when it cannot be had. */
    private String tokenOrUnavailable(UUID keycloakId) {
        try {
            return serviceAccountToken();
        } catch (RestClientException e) {
            log.warn("Keycloak token for {} failed: {}", keycloakId, statusOf(e));
            throw new ApiException(ErrorCode.KEYCLOAK_UNAVAILABLE);
        }
    }

    /** Keycloak's own "User not found" 404, as opposed to a 404 from a wrong path or proxy. */
    private static boolean isUserNotFound(HttpClientErrorException.NotFound e) {
        return e.getResponseBodyAsString().toLowerCase().contains("not found");
    }

    private <T> T fetchAdmin(String path, UUID keycloakId, Class<T> type) {
        return fetchAdmin(path, keycloakId, tokenOrUnavailable(keycloakId), type);
    }

    private <T> T fetchAdmin(String path, UUID keycloakId, String token, Class<T> type) {
        return restClient.get()
                .uri(path, realm, keycloakId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(type);
    }

    /** Erases the account at Keycloak; an already missing user (404) counts as success. */
    public void deleteUser(UUID keycloakId) {
        String token = tokenOrUnavailable(keycloakId);
        try {
            restClient.delete()
                    .uri("/admin/realms/{realm}/users/{id}", realm, keycloakId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            if (!isUserNotFound(e)) {
                log.warn("Keycloak deletion of {} answered an unexpected 404", keycloakId);
                throw new ApiException(ErrorCode.KEYCLOAK_UPDATE_FAILED);
            }
            log.info("Keycloak user {} was already gone", keycloakId);
        } catch (RestClientException e) {
            log.warn("Keycloak rejected deletion of {}: {}", keycloakId, statusOf(e));
            throw new ApiException(ErrorCode.KEYCLOAK_UPDATE_FAILED);
        }
    }

    /** HTTP status only: exception messages can echo request or response content. */
    private static String statusOf(RestClientException e) {
        return e instanceof RestClientResponseException r ? String.valueOf(r.getStatusCode().value())
                : "no response";
    }

    /**
     * Changes the password: rejects with INVALID_CREDENTIALS when the current password
     * does not authenticate, then resets via the Admin API.
     */
    public void changePassword(String email, String currentPassword, UUID keycloakId, String newPassword,
            String keepSid) {
        verifyCurrentPassword(email, currentPassword);
        resetPassword(keycloakId, newPassword);
        log.info("Password changed for keycloak user {}", keycloakId);
        logoutOtherSessions(keycloakId, keepSid);
        revokeOfflineTokens(keycloakId);
    }

    /**
     * Revokes the user's offline tokens for every client that holds one (GET consents lists them
     * as additionalGrants, DELETE consents/{clientId} revokes). Best effort: failures are logged.
     */
    public void revokeOfflineTokens(UUID keycloakId) {
        try {
            String token = serviceAccountToken();
            List<?> consents = restClient.get()
                    .uri("/admin/realms/{realm}/users/{id}/consents", realm, keycloakId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(List.class);
            for (Object consent : consents == null ? List.of() : consents) {
                if (consent instanceof Map<?, ?> c && c.get("clientId") != null && holdsOfflineToken(c)) {
                    revokeOfflineToken(token, keycloakId, c.get("clientId").toString());
                }
            }
        } catch (RestClientException e) {
            log.warn("Offline tokens of keycloak user {} not revoked: {}", keycloakId, statusOf(e));
        } catch (ApiException e) {
            log.warn("Offline tokens of keycloak user {} not revoked: no service token", keycloakId);
        }
    }

    private static boolean holdsOfflineToken(Map<?, ?> consent) {
        return consent.get("additionalGrants") instanceof List<?> grants
                && grants.stream().anyMatch(g -> g instanceof Map<?, ?> m && "Offline Token".equals(m.get("key")));
    }

    private void revokeOfflineToken(String token, UUID keycloakId, String clientId) {
        try {
            restClient.delete()
                    .uri("/admin/realms/{realm}/users/{id}/consents/{client}", realm, keycloakId, clientId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Offline token of keycloak user {} for a client not revoked: {}", keycloakId, statusOf(e));
        }
    }

    /**
     * Ends every session of the user except {@code keepSid}. Best effort: the password is
     * already changed, so a failure is logged and swallowed. Without a sid nothing is ended.
     */
    public void logoutOtherSessions(UUID keycloakId, String keepSid) {
        if (keepSid == null || keepSid.isBlank()) {
            log.warn("No sid in the token of keycloak user {}; other sessions not ended", keycloakId);
            return;
        }
        try {
            String token = serviceAccountToken();
            List<?> sessions = restClient.get()
                    .uri("/admin/realms/{realm}/users/{id}/sessions", realm, keycloakId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(List.class);
            for (Object session : sessions == null ? List.of() : sessions) {
                if (session instanceof Map<?, ?> s && s.get("id") != null && !keepSid.equals(s.get("id"))) {
                    restClient.delete()
                            .uri("/admin/realms/{realm}/sessions/{sid}", realm, s.get("id"))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .retrieve()
                            .toBodilessEntity();
                }
            }
        } catch (RestClientException e) {
            log.warn("Other sessions of keycloak user {} not ended: {}", keycloakId, statusOf(e));
        } catch (ApiException e) {
            log.warn("Other sessions of keycloak user {} not ended: no service token", keycloakId);
        }
    }

    /** Direct-grant login with the given password; INVALID_CREDENTIALS when it does not authenticate. */
    public void verifyCurrentPassword(String email, String currentPassword) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", verifyClientId);
        form.add("username", email);
        form.add("password", currentPassword);
        try {
            restClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        } catch (RestClientException e) {
            log.warn("Password verification failed against Keycloak: {}", statusOf(e));
            throw new ApiException(ErrorCode.KEYCLOAK_UPDATE_FAILED);
        }
    }

    private void resetPassword(UUID keycloakId, String newPassword) {
        try {
            restClient.put()
                    .uri("/admin/realms/{realm}/users/{id}/reset-password", realm, keycloakId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceAccountToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("type", "password", "value", newPassword, "temporary", false))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Keycloak rejected password reset: {}", statusOf(e));
            throw new ApiException(ErrorCode.KEYCLOAK_UPDATE_FAILED);
        }
    }

    private String serviceAccountToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", adminClientId);
        form.add("client_secret", adminClientSecret);
        Map<?, ?> response = restClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        if (response == null || response.get("access_token") == null) {
            throw new ApiException(ErrorCode.KEYCLOAK_UPDATE_FAILED);
        }
        return response.get("access_token").toString();
    }
}
