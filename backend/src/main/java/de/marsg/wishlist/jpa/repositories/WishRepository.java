package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.WishEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
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

    List<WishEntity> findByWishlistsIdIn(Collection<UUID> wishlistsIds);

    long countByWishlistsId(UUID wishlistsId);

    /** Non-deleted wishes in the user's non-deleted wishlists (soft-delete restrictions apply). */
    @Query("""
            SELECT COUNT(w) FROM WishEntity w
            WHERE w.wishlistsId IN (SELECT l.id FROM WishlistEntity l WHERE l.usersId = :userId)
            """)
    long countInWishlistsOf(@Param("userId") UUID userId);

    /** The live (not soft-deleted) wish holding the link. */
    @Query("SELECT w FROM WishEntity w JOIN w.links l WHERE l.id = :linkId")
    Optional<WishEntity> findByLinkId(@Param("linkId") UUID linkId);

    /** Serializes claim capacity checks on one wish. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WishEntity w WHERE w.id = :id")
    Optional<WishEntity> findByIdForUpdate(@Param("id") UUID id);
}
