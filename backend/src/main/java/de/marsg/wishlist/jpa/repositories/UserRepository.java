package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.UserEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByKeycloakId(UUID keycloakId);

    /** Serializes group/wishlist limit checks per user. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserEntity u WHERE u.id = :id")
    Optional<UserEntity> findByIdForUpdate(@Param("id") UUID id);

    /** Bypasses the soft-delete restriction: admin export and erasure reach deleted accounts too. */
    @Query(value = "SELECT * FROM users WHERE id = :id", nativeQuery = true)
    Optional<UserEntity> findByIdIncludingDeleted(@Param("id") UUID id);

    /** Admin search including soft-deleted rows; {@code pattern} is a LIKE pattern, already escaped. */
    @Query(value = "SELECT * FROM users WHERE lower(email) LIKE lower(:pattern)",
            countQuery = "SELECT count(*) FROM users WHERE lower(email) LIKE lower(:pattern)",
            nativeQuery = true)
    Page<UserEntity> searchByEmailIncludingDeleted(@Param("pattern") String pattern, Pageable pageable);

    /** Hard delete by id; DB cascades remove everything the user owns. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM users WHERE id = :id", nativeQuery = true)
    int deleteHardById(@Param("id") UUID id);

    /** Admin search: email only, partial match. */
    Page<UserEntity> findByEmailContainingIgnoreCase(String email, Pageable pageable);

    /** Includes soft-deleted rows: a deleted account still owns its email until purged. */
    @Query(value = "SELECT * FROM users WHERE lower(email) = lower(:email)", nativeQuery = true)
    Optional<UserEntity> findByEmailIncludingDeleted(@Param("email") String email);

    /**
     * Bypasses the soft-delete restriction: used by JIT provisioning to restore a
     * soft-deleted account when the user logs in again within the purge window.
     */
    @Query(value = "SELECT * FROM users WHERE keycloak_id = :keycloakId", nativeQuery = true)
    Optional<UserEntity> findByKeycloakIdIncludingDeleted(@Param("keycloakId") UUID keycloakId);
}
