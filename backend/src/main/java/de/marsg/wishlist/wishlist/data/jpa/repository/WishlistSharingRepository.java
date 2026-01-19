package de.marsg.wishlist.wishlist.data.jpa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.marsg.wishlist.wishlist.data.jpa.entity.Group;
import de.marsg.wishlist.wishlist.data.jpa.entity.Wishlist;
import de.marsg.wishlist.wishlist.data.jpa.entity.WishlistSharing;
import de.marsg.wishlist.wishlist.data.jpa.entity.special_ids.WishlistSharingId;

import jakarta.transaction.Transactional;

public interface WishlistSharingRepository extends JpaRepository<WishlistSharing, WishlistSharingId> {

    @Query(value = "SELECT w.* FROM wishlists w JOIN wishlist_sharing ws ON w.id = ws.wishlist_id WHERE ws.group_id = :groupId LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<Wishlist> findAllByGroupId(@Param("groupId") UUID groupId, @Param("limit") int limit,
            @Param("offset") int offset);

    default List<Wishlist> findAllByGroupId(@Param("groupId") UUID groupId, @Param("offset") int offset) {
        return findAllByGroupId(groupId, 20, offset);
    }

    @Query(value = "SELECT g.* FROM groups g JOIN wishlist_sharing ws ON g.id = ws.group_id WHERE ws.wishlist_id = :wishlistId", nativeQuery = true)
    List<Group> findAllByWishlistId(@Param("wishlistId") UUID wishlistId);

    @Query(value = "SELECT COUNT(g.*) FROM groups g JOIN wishlist_sharing ws ON g.id = ws.group_id WHERE ws.wishlist_id = :wishlistId", nativeQuery = true)
    long countAllByWishlistId(@Param("wishlistId") UUID wishlistId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM wishlist_sharing WHERE wishlist_id = :wishlistId", nativeQuery = true)
    void deleteAllByWishlistId(@Param("wishlistId") UUID wishlistId);

    @Query("SELECT CASE WHEN COUNT(w) > 0 THEN true ELSE false END FROM Wishlist w WHERE w.id = :wishlistId AND w.owner.id = :userId")
    boolean isUserOwnerOfWishlist(@Param("wishlistId") UUID wishlistId, @Param("userId") UUID userId);

    @Query(value = "SELECT CASE WHEN COUNT(ws.*) > 0 THEN true ELSE false END FROM wishlist_sharing ws JOIN group_memberships gm ON ws.group_id = gm.group_id WHERE ws.wishlist_id = :wishlistId AND gm.user_id = :userId", nativeQuery = true)
    boolean isUserInSharedWishlistGroup(@Param("wishlistId") UUID wishlistId, @Param("userId") UUID userId);

}
