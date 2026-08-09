package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * All Keycloak interactions of the backend. Keycloak stays the source of truth for
 * account data; our DB copies are only written after Keycloak accepted a change.
 *
 * Email: Account API with the user's own forwarded token.
 * Password: current password is verified via a direct-grant login, then the new
 * password is set through the Admin API using the backend's service account —
 * so the password UI can live inside the app without any redirect to Keycloak.
 * Locale: mirrored through the Admin API; our DB owns the value (see UserService).
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
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.realm = realm;
        this.adminClientId = adminClientId;
        this.adminClientSecret = adminClientSecret;
        this.verifyClientId = verifyClientId;
    }

    /** Updates the account email (which is also the username). Throws on any Keycloak rejection. */
    public void updateEmail(String bearerToken, String newEmail) {
        try {
            restClient.post()
                    .uri("/realms/{realm}/account", realm)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", newEmail, "username", newEmail))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Keycloak rejected email update: {}", e.getMessage());
            throw new ApiException(ErrorCode.KEYCLOAK_UPDATE_FAILED);
        }
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
        String token = serviceAccountToken();
        try {
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
            Map<String, Object> attributes = new HashMap<>();
            if (user.get("attributes") instanceof Map<?, ?> existing) {
                existing.forEach((key, value) -> attributes.put(String.valueOf(key), value));
            }
            attributes.put("locale", List.of(locale));
            user.put("attributes", attributes);
            restClient.put()
                    .uri("/admin/realms/{realm}/users/{id}", realm, keycloakId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(user)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Keycloak rejected locale update for {}: {}", keycloakId, e.getMessage());
            throw new ApiException(ErrorCode.KEYCLOAK_UPDATE_FAILED);
        }
    }

    /**
     * Changes the password: rejects with INVALID_CREDENTIALS when the current password
     * does not authenticate, then resets via the Admin API.
     */
    public void changePassword(String email, String currentPassword, UUID keycloakId, String newPassword) {
        verifyCurrentPassword(email, currentPassword);
        resetPassword(keycloakId, newPassword);
        log.info("Password changed for keycloak user {}", keycloakId);
    }

    private void verifyCurrentPassword(String email, String currentPassword) {
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
            log.warn("Password verification failed against Keycloak: {}", e.getMessage());
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
            log.warn("Keycloak rejected password reset: {}", e.getMessage());
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
