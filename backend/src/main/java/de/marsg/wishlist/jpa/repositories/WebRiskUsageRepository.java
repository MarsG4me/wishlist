package de.marsg.wishlist.jpa.repositories;

import de.marsg.wishlist.jpa.entities.WishLinkEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Monthly Web Risk call counter ({@code web_risk_usage}, one row per UTC month). */
public interface WebRiskUsageRepository extends Repository<WishLinkEntity, UUID> {

    /** Atomically counts one call; returns 0 when this month's limit is already spent. */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO web_risk_usage (month, calls)
            VALUES (CAST(date_trunc('month', now() AT TIME ZONE 'UTC') AS date), 1)
            ON CONFLICT (month) DO UPDATE SET calls = web_risk_usage.calls + 1
            WHERE web_risk_usage.calls < :limit
            """, nativeQuery = true)
    int tryReserve(@Param("limit") int limit);

    @Query(value = "SELECT calls FROM web_risk_usage "
            + "WHERE month = CAST(date_trunc('month', now() AT TIME ZONE 'UTC') AS date)", nativeQuery = true)
    Optional<Integer> findCallsThisMonth();
}
