package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.WishLinkEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Admin-blocked domains ({@code blocked_domains}); rows are plain tuples, the id carries the block time. */
public interface BlockedDomainRepository extends Repository<WishLinkEntity, UUID> {

    @Query(value = "SELECT host FROM blocked_domains WHERE host IN (:hosts)", nativeQuery = true)
    List<String> findBlockedAmong(@Param("hosts") Collection<String> hosts);

    /** Idempotent: a host that is already blocked keeps its original row. */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO blocked_domains (host, admin_users_id, link_reports_id)
            VALUES (:host, :adminId, :reportId) ON CONFLICT (host) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("host") String host, @Param("adminId") UUID adminId,
            @Param("reportId") UUID reportId);

    @Query(value = "SELECT host FROM blocked_domains WHERE id = :id", nativeQuery = true)
    Optional<String> findHostById(@Param("id") UUID id);

    @Transactional
    @Modifying
    @Query(value = "DELETE FROM blocked_domains WHERE id = :id", nativeQuery = true)
    int deleteBlockedById(@Param("id") UUID id);

    /** Rows of {id, host, link_reports_id}, newest first. */
    @Query(value = "SELECT id, host, link_reports_id FROM blocked_domains ORDER BY id DESC",
            countQuery = "SELECT count(*) FROM blocked_domains", nativeQuery = true)
    Page<Object[]> findPage(Pageable pageable);
}
