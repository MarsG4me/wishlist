package de.marsg.wishlist.configs.security;

import de.marsg.wishlist.jpa.entities.UserTier;
import java.util.UUID;

/**
 * The provisioned application user behind the current JWT. Injected into controller
 * methods by {@link CurrentUserArgumentResolver}.
 *
 * @param identityProvider alias of the external login (Google, Apple, …) the session
 *                         came from, or {@code null} for a local account. Credentials
 *                         of federated accounts live at the provider, so we neither
 *                         change nor store them.
 * @param sessionId        Keycloak session id from the JWT {@code sid} claim, or {@code null}
 */
public record AuthenticatedUser(
        UUID userId,
        UUID keycloakId,
        String displayname,
        UserTier tier,
        boolean isAdmin,
        String identityProvider,
        String sessionId) {

    public boolean isExternallyManaged() {
        return identityProvider != null && !identityProvider.isBlank();
    }
}
