package de.marsg.wishlist.wishlist.data.jpa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.marsg.wishlist.wishlist.data.jpa.entity.User;
import de.marsg.wishlist.wishlist.data.jpa.entity.Wishlist;

@Repository
public interface WishlistRepository extends JpaRepository<Wishlist, UUID> {

    @Query(value = "SELECT w.* FROM wishlists w WHERE w.owner_id = :ownerId LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Wishlist> findAllByOwner(@Param("ownerId") UUID ownerId, @Param("limit") int limit, @Param("offset") int offset);

    default List<Wishlist> findAllByOwner(@Param("ownerId") UUID ownerId, @Param("offset") int offset) {
        return findAllByOwner(ownerId, 20, offset);
    }

    @Query("SELECT CASE WHEN COUNT(w) > 0 THEN true ELSE false END FROM Wishlist w WHERE w.id = :wishlistId AND w.owner.id = :ownerId")
    boolean isWishlistOwnedBy(@Param("wishlistId") UUID wishlistId, @Param("ownerId") UUID ownerId);

    int countByOwner(User owner);
}
