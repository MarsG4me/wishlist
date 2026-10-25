package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.GroupInviteEntity;
import de.marsg.wishlist.jpa.entities.UserEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only queries that gather a user's personal data across tables for the export. */
public interface UserExportRepository extends Repository<UserEntity, UUID> {

    /** Rows of {group id, name, is_admin, owner id, membership id}, soft-deleted groups included. */
    @Query(value = """
            SELECT g.id, g.name, m.is_admin, g.users_id, m.id
            FROM group_memberships m JOIN groups g ON g.id = m.groups_id
            WHERE m.users_id = :userId ORDER BY m.id
            """, nativeQuery = true)
    List<Object[]> findMembershipsWithGroups(@Param("userId") UUID userId);

    /** Rows of {id, name, description, deleted_at}; soft-deleted groups are still stored, so included. */
    @Query(value = "SELECT id, name, description, deleted_at FROM groups WHERE users_id = :userId ORDER BY id",
            nativeQuery = true)
    List<Object[]> findGroupRowsCreatedBy(@Param("userId") UUID userId);

    /** Rows of {id, name, deleted_at} of every wishlist of the user, soft-deleted ones included. */
    @Query(value = "SELECT id, name, deleted_at FROM wishlists WHERE users_id = :userId ORDER BY id",
            nativeQuery = true)
    List<Object[]> findWishlistRows(@Param("userId") UUID userId);

    /** Rows of {id, wishlists_id, name, description, quantity, deleted_at}, soft-deleted ones included. */
    @Query(value = """
            SELECT w.id, w.wishlists_id, w.name, w.description, w.quantity, w.deleted_at
            FROM wishes w JOIN wishlists l ON l.id = w.wishlists_id
            WHERE l.users_id = :userId ORDER BY w.id
            """, nativeQuery = true)
    List<Object[]> findWishRows(@Param("userId") UUID userId);

    /** Rows of {wishes_id, url, label, position} for every wish of the user, ordered by position. */
    @Query(value = """
            SELECT k.wishes_id, k.url, k.label, k.position
            FROM wish_links k JOIN wishes w ON w.id = k.wishes_id
            JOIN wishlists l ON l.id = w.wishlists_id
            WHERE l.users_id = :userId ORDER BY k.wishes_id, k.position
            """, nativeQuery = true)
    List<Object[]> findLinkRows(@Param("userId") UUID userId);

    /** Rows of {wishlists_id, groups_id} of every share of the user's wishlists, soft-deleted ones included. */
    @Query(value = """
            SELECT s.wishlists_id, s.groups_id
            FROM group_wishlist_shares s JOIN wishlists l ON l.id = s.wishlists_id
            WHERE l.users_id = :userId ORDER BY s.id
            """, nativeQuery = true)
    List<Object[]> findShareRows(@Param("userId") UUID userId);

    @Query("SELECT i FROM GroupInviteEntity i WHERE i.usersId = :userId")
    List<GroupInviteEntity> findInvitesCreatedBy(@Param("userId") UUID userId);

    /** Rows of {claim id, wish name, owner displayname or null, is_shared, claim creator id}, deleted wishes included. */
    @Query(value = """
            SELECT c.id, w.name, o.displayname, c.is_shared, c.users_id
            FROM claim_participants p
            JOIN claims c ON c.id = p.claims_id
            JOIN wishes w ON w.id = c.wishes_id
            JOIN wishlists wl ON wl.id = w.wishlists_id
            LEFT JOIN users o ON o.id = wl.users_id AND o.deleted_at IS NULL
            WHERE p.users_id = :userId ORDER BY c.id
            """, nativeQuery = true)
    List<Object[]> findClaimRows(@Param("userId") UUID userId);
}
