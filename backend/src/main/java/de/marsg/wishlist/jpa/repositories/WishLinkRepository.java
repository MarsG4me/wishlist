package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.WishLinkEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Bulk flag updates for saved links; blocked_by is only ever written through these statements. */
public interface WishLinkRepository extends JpaRepository<WishLinkEntity, UUID> {

    @Query(value = "SELECT DISTINCT host FROM wish_links WHERE host IS NOT NULL", nativeQuery = true)
    List<String> findDistinctHosts();

    /** Those of the given URLs that any wish has saved as a link blocked by an admin. */
    @Query(value = "SELECT DISTINCT url FROM wish_links WHERE blocked_by = 'ADMIN' AND url IN (:urls)",
            nativeQuery = true)
    List<String> findAdminBlockedUrls(@Param("urls") Collection<String> urls);

    /** The host itself and every subdomain of it that saved links use. */
    @Query(value = "SELECT DISTINCT host FROM wish_links WHERE host = :domain OR host LIKE CONCAT('%.', :domain)",
            nativeQuery = true)
    List<String> findHostsUnder(@Param("domain") String domain);

    /** Rows of {id, url} without a host yet, id-ordered after {@code afterId}. */
    @Query(value = "SELECT id, url FROM wish_links WHERE host IS NULL AND id > :afterId ORDER BY id LIMIT :max",
            nativeQuery = true)
    List<Object[]> findWithoutHost(@Param("afterId") UUID afterId, @Param("max") int max);

    @Transactional
    @Modifying
    @Query(value = "UPDATE wish_links SET host = :host WHERE id = :id", nativeQuery = true)
    int setHost(@Param("id") UUID id, @Param("host") String host);

    /** Domain-block flag on the host's links; overwrites automatic flags, keeps single-link ADMIN blocks. */
    @Transactional
    @Modifying
    @Query(value = "UPDATE wish_links SET blocked_by = 'ADMIN_DOMAIN' WHERE host = :host "
            + "AND blocked_by IS DISTINCT FROM 'ADMIN' AND blocked_by IS DISTINCT FROM 'ADMIN_DOMAIN'",
            nativeQuery = true)
    int flagHostAsAdminDomain(@Param("host") String host);

    /** Sets an automatic source on the host's links, never touching ADMIN flags. */
    @Transactional
    @Modifying
    @Query(value = """
            UPDATE wish_links SET blocked_by = CAST(:source AS link_block_source)
            WHERE host = :host AND blocked_by IS DISTINCT FROM CAST(:source AS link_block_source)
              AND blocked_by IS DISTINCT FROM 'ADMIN' AND blocked_by IS DISTINCT FROM 'ADMIN_DOMAIN'
            """, nativeQuery = true)
    int flagHostAutomatically(@Param("host") String host, @Param("source") String source);

    /** Clears automatic flags of the host's links; ADMIN and ADMIN_DOMAIN flags stay. */
    @Transactional
    @Modifying
    @Query(value = "UPDATE wish_links SET blocked_by = NULL WHERE host = :host "
            + "AND blocked_by NOT IN ('ADMIN', 'ADMIN_DOMAIN')", nativeQuery = true)
    int clearAutomaticFlags(@Param("host") String host);

    /** Like clearAutomaticFlags, but BLOCKLIST flags stay (the blocklist did not load completely). */
    @Transactional
    @Modifying
    @Query(value = "UPDATE wish_links SET blocked_by = NULL WHERE host = :host "
            + "AND blocked_by NOT IN ('ADMIN', 'ADMIN_DOMAIN', 'BLOCKLIST')", nativeQuery = true)
    int clearAutomaticFlagsKeepingBlocklist(@Param("host") String host);

    /** Transaction-scoped advisory lock on the host; serializes rescans against domain unblocks. */
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:host))) AS locked", nativeQuery = true)
    int lockHost(@Param("host") String host);

    /** Lifts the domain-block flag on the host's links; single-link ADMIN blocks stay. */
    @Transactional
    @Modifying
    @Query(value = "UPDATE wish_links SET blocked_by = NULL WHERE host = :host AND blocked_by = 'ADMIN_DOMAIN'",
            nativeQuery = true)
    int clearAdminDomainFlags(@Param("host") String host);

    @Transactional
    @Modifying
    @Query(value = "UPDATE wish_links SET blocked_by = 'ADMIN' WHERE id = :id "
            + "AND blocked_by IS DISTINCT FROM 'ADMIN'", nativeQuery = true)
    int flagAsAdmin(@Param("id") UUID id);

    @Transactional
    @Modifying
    @Query(value = "UPDATE wish_links SET blocked_by = NULL WHERE id = :id AND blocked_by = 'ADMIN'",
            nativeQuery = true)
    int clearAdminFlag(@Param("id") UUID id);
}
