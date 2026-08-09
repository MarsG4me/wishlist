package de.marsg.wishlist.configs.security;

import de.marsg.wishlist.jpa.entities.UserTier;
import java.util.UUID;

/**
 * The provisioned application user behind the current JWT. Injected into controller
 * methods by {@link CurrentUserArgumentResolver}.
 */
public record AuthenticatedUser(
        UUID userId,
        UUID keycloakId,
        String displayname,
        UserTier tier,
        boolean isAdmin) {
}
