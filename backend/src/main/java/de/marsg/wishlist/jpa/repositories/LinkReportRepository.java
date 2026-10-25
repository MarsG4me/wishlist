package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.LinkReportEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LinkReportRepository extends JpaRepository<LinkReportEntity, UUID> {

    /** Row-locks the report so concurrent decisions queue up and the loser sees the decided status. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM LinkReportEntity r WHERE r.id = :id")
    Optional<LinkReportEntity> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByWishLinksIdAndUsersId(UUID wishLinksId, UUID usersId);

    /**
     * Admin list columns: {id, status, reason, comment, link id, url, host, blocked_by, wish id, wish title,
     * owner displayname, reporter displayname, decided_at}; gone link or wish fall back to the evidence.
     */
    String ITEM_SELECT = """
            SELECT r.id, CAST(r.status AS text), CAST(r.reason AS text), r.comment, r.wish_links_id,
                   COALESCE(k.url, r.evidence->>'url'), COALESCE(k.host, r.evidence->>'host'),
                   CAST(k.blocked_by AS text),
                   COALESCE(w.id, CAST(r.evidence->>'wish_id' AS uuid)), COALESCE(w.name, r.evidence->>'wish_title'),
                   COALESCE(o.displayname, r.evidence->>'owner_displayname'), rp.displayname, r.decided_at
            FROM link_reports r
            LEFT JOIN wish_links k ON k.id = r.wish_links_id
            LEFT JOIN wishes w ON w.id = k.wishes_id
            LEFT JOIN wishlists l ON l.id = w.wishlists_id
            LEFT JOIN users o ON o.id = l.users_id
            LEFT JOIN users rp ON rp.id = r.users_id
            """;

    /** Newest first; a null status means all. */
    @Query(value = ITEM_SELECT + " WHERE CAST(:status AS text) IS NULL OR CAST(r.status AS text) = :status "
            + "ORDER BY r.id DESC",
            countQuery = "SELECT count(*) FROM link_reports r "
                    + "WHERE CAST(:status AS text) IS NULL OR CAST(r.status AS text) = :status",
            nativeQuery = true)
    Page<Object[]> findItems(@Param("status") String status, Pageable pageable);

    @Query(value = ITEM_SELECT + " WHERE r.id = :id", nativeQuery = true)
    List<Object[]> findItem(@Param("id") UUID id);

    /** One row of {link id, url, host, wish id, wish title, wishlist id, wishlist title, owner id, owner email, owner displayname}. */
    @Query(value = """
            SELECT k.id, k.url, k.host, w.id, w.name, l.id, l.name, o.id, o.email, o.displayname
            FROM link_reports r
            JOIN wish_links k ON k.id = r.wish_links_id
            JOIN wishes w ON w.id = k.wishes_id
            JOIN wishlists l ON l.id = w.wishlists_id
            JOIN users o ON o.id = l.users_id
            WHERE r.id = :id
            """, nativeQuery = true)
    List<Object[]> findEvidenceSource(@Param("id") UUID id);
}
