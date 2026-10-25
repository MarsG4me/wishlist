package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.WishLinkEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Stored Web Risk verdict per host ({@code link_host_checks}); each call is its own transaction. */
public interface LinkHostCheckRepository extends Repository<WishLinkEntity, UUID> {

    @Query(value = "SELECT CAST(verdict AS text) FROM link_host_checks WHERE host = :host", nativeQuery = true)
    Optional<String> findVerdict(@Param("host") String host);

    /** {@code verdict} is SAFE or UNSAFE; refreshes checked_at. */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO link_host_checks (host, verdict, checked_at)
            VALUES (:host, CAST(:verdict AS link_host_verdict), now())
            ON CONFLICT (host) DO UPDATE SET verdict = EXCLUDED.verdict, checked_at = now()
            """, nativeQuery = true)
    int upsertVerdict(@Param("host") String host, @Param("verdict") String verdict);

    /** Oldest first; only hosts some saved link still uses are worth another lookup. */
    @Query(value = """
            SELECT h.host FROM link_host_checks h
            WHERE h.checked_at < :before AND EXISTS (SELECT 1 FROM wish_links k WHERE k.host = h.host)
            ORDER BY h.checked_at LIMIT :max
            """, nativeQuery = true)
    List<String> findOldestHostsUsedByLinks(@Param("before") Instant before, @Param("max") int max);

    @Transactional
    @Modifying
    @Query(value = "DELETE FROM link_host_checks WHERE host = :host", nativeQuery = true)
    int deleteByHost(@Param("host") String host);
}
