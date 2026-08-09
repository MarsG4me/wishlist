package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.GroupWishlistShareEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupWishlistShareRepository extends JpaRepository<GroupWishlistShareEntity, UUID> {

    List<GroupWishlistShareEntity> findByGroupsId(UUID groupsId);

    List<GroupWishlistShareEntity> findByWishlistsId(UUID wishlistsId);

    Optional<GroupWishlistShareEntity> findByGroupsIdAndWishlistsId(UUID groupsId, UUID wishlistsId);

    boolean existsByGroupsIdAndWishlistsId(UUID groupsId, UUID wishlistsId);

    /** True when the wishlist is shared into at least one non-deleted group the user is member of. */
    @Query("""
            SELECT COUNT(s) > 0 FROM GroupWishlistShareEntity s
            JOIN GroupMembershipEntity m ON m.groupsId = s.groupsId
            JOIN GroupEntity g ON g.id = s.groupsId
            WHERE s.wishlistsId = :wishlistId AND m.usersId = :userId
            """)
    boolean existsShareGivingUserAccess(@Param("userId") UUID userId, @Param("wishlistId") UUID wishlistId);
}
