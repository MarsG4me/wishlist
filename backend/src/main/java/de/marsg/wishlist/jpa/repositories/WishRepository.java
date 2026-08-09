package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.WishEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WishRepository extends JpaRepository<WishEntity, UUID> {

    Page<WishEntity> findByWishlistsId(UUID wishlistsId, Pageable pageable);

    long countByWishlistsId(UUID wishlistsId);

    /** Serializes claim capacity checks on one wish. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WishEntity w WHERE w.id = :id")
    Optional<WishEntity> findByIdForUpdate(@Param("id") UUID id);
}
