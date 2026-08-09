package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.jpa.entities.UserEntity;
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
                .map(deleted -> {
                    deleted.restore();
                    log.info("Restored soft-deleted user {}", deleted.getId());
                    return userRepository.save(deleted);
                })
                .orElseGet(() -> {
                    UserEntity created = userRepository.save(
                            new UserEntity(keycloakId, displaynameFrom(jwt), emailFrom(jwt)));
                    log.info("Provisioned new user {}", created.getId());
                    return created;
                });
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
