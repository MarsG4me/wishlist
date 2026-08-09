package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.jpa.entities.UserEntity;
import de.marsg.wishlist.jpa.entities.UserLocale;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Just-in-time user provisioning: the first authenticated request of a Keycloak user
 * creates the application user. A soft-deleted account is restored on re-login
 * (deletion only becomes final after the 30-day purge).
 */
@Service
public class UserProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(UserProvisioningService.class);

    private final UserRepository userRepository;

    public UserProvisioningService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserEntity provision(Jwt jwt) {
        UUID keycloakId = UUID.fromString(jwt.getSubject());
        return userRepository.findByKeycloakId(keycloakId)
                .orElseGet(() -> restoreOrCreate(jwt, keycloakId));
    }

    private UserEntity restoreOrCreate(Jwt jwt, UUID keycloakId) {
        return userRepository.findByKeycloakIdIncludingDeleted(keycloakId)
                .map(this::restore)
                .orElseGet(() -> create(jwt, keycloakId));
    }

    private UserEntity restore(UserEntity deleted) {
        deleted.restore();
        log.info("Restored soft-deleted user {}", deleted.getId());
        return userRepository.save(deleted);
    }

    /**
     * An email belongs to exactly one account. If it is already taken — including by a
     * soft-deleted account inside the 30-day window — the user must sign in with the
     * original login to reactivate rather than create a second account.
     */
    private UserEntity create(Jwt jwt, UUID keycloakId) {
        String email = emailFrom(jwt);
        userRepository.findByEmailIncludingDeleted(email).ifPresent(existing -> {
            log.warn("Rejected provisioning for keycloak user {}: email already used by user {}",
                    keycloakId, existing.getId());
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        });
        UserEntity created = userRepository.save(new UserEntity(keycloakId, displaynameFrom(jwt), email,
                UserLocale.fromTag(jwt.getClaimAsString("locale"))));
        log.info("Provisioned new user {}", created.getId());
        return created;
    }

    private String displaynameFrom(Jwt jwt) {
        String given = jwt.getClaimAsString("given_name");
        String family = jwt.getClaimAsString("family_name");
        String name = ((given == null ? "" : given) + " " + (family == null ? "" : family)).trim();
        if (name.isEmpty()) {
            name = jwt.getClaimAsString("preferred_username");
        }
        if (name == null || name.isEmpty()) {
            name = "User";
        }
        return name.length() > 60 ? name.substring(0, 60) : name;
    }

    private String emailFrom(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isEmpty()) {
            // email is the Keycloak username; fall back to preferred_username
            email = jwt.getClaimAsString("preferred_username");
        }
        return email == null ? "" : email;
    }
}
