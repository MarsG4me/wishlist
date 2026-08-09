package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByKeycloakId(UUID keycloakId);

    /**
     * Bypasses the soft-delete restriction: used by JIT provisioning to restore a
     * soft-deleted account when the user logs in again within the purge window.
     */
    @Query(value = "SELECT * FROM users WHERE keycloak_id = :keycloakId", nativeQuery = true)
    Optional<UserEntity> findByKeycloakIdIncludingDeleted(@Param("keycloakId") UUID keycloakId);
}
