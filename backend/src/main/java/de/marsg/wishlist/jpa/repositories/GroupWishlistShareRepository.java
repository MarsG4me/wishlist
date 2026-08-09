package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.GroupWishlistShareEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupWishlistShareRepository extends JpaRepository<GroupWishlistShareEntity, UUID> {

    Page<GroupWishlistShareEntity> findByGroupsId(UUID groupsId, Pageable pageable);

    List<GroupWishlistShareEntity> findByWishlistsId(UUID wishlistsId);

    Optional<GroupWishlistShareEntity> findByGroupsIdAndWishlistsId(UUID groupsId, UUID wishlistsId);

    boolean existsByGroupsIdAndWishlistsId(UUID groupsId, UUID wishlistsId);

    /** Shares of a user's wishlists inside one group — removed when they leave it. */
    @Query("""
            SELECT s FROM GroupWishlistShareEntity s
            JOIN WishlistEntity w ON w.id = s.wishlistsId
            WHERE s.groupsId = :groupId AND w.usersId = :userId
            """)
    List<GroupWishlistShareEntity> findSharesOfUserInGroup(@Param("userId") UUID userId,
            @Param("groupId") UUID groupId);

    /** Every share of a user's wishlists — removed when the account is deleted. */
    @Query("""
            SELECT s FROM GroupWishlistShareEntity s
            JOIN WishlistEntity w ON w.id = s.wishlistsId
            WHERE w.usersId = :userId
            """)
    List<GroupWishlistShareEntity> findSharesOfUser(@Param("userId") UUID userId);

    /** True when the wishlist is shared into at least one non-deleted group the user is member of. */
    @Query("""
            SELECT COUNT(s) > 0 FROM GroupWishlistShareEntity s
            JOIN GroupMembershipEntity m ON m.groupsId = s.groupsId
            JOIN GroupEntity g ON g.id = s.groupsId
            WHERE s.wishlistsId = :wishlistId AND m.usersId = :userId
            """)
    boolean existsShareGivingUserAccess(@Param("userId") UUID userId, @Param("wishlistId") UUID wishlistId);
}
