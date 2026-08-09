package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.WishlistEntity;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WishlistRepository extends JpaRepository<WishlistEntity, UUID> {

    Page<WishlistEntity> findByUsersId(UUID usersId, Pageable pageable);

    long countByUsersId(UUID usersId);

    /** Own wishlists not yet shared into the given group — filtered in SQL, not client-side. */
    @Query("""
            SELECT w FROM WishlistEntity w
            WHERE w.usersId = :userId
              AND NOT EXISTS (
                  SELECT 1 FROM GroupWishlistShareEntity s
                  WHERE s.wishlistsId = w.id AND s.groupsId = :groupId)
            """)
    Page<WishlistEntity> findShareableIntoGroup(@Param("userId") UUID userId,
            @Param("groupId") UUID groupId, Pageable pageable);
}
