package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Talks to the Keycloak Account API on behalf of the user (their bearer token is
 * forwarded). Keycloak is the source of truth for the email/username; our DB copy is
 * only written after Keycloak accepted the change.
 */
@Component
public class KeycloakAccountClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAccountClient.class);

    private final RestClient restClient;

    public KeycloakAccountClient(@Value("${wishlist.keycloak.account-url}") String accountUrl) {
        this.restClient = RestClient.builder().baseUrl(accountUrl).build();
    }

    /** Updates the account email (which is also the username). Throws on any Keycloak rejection. */
    public void updateEmail(String bearerToken, String newEmail) {
        try {
            restClient.post()
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
}
