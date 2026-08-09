package de.marsg.wishlist.helpers;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opt-in automatic variant of scripts/purge_soft_deleted.sql (enable with
 * wishlist.purge.enabled=true). Deletion order matters only for log clarity —
 * FK cascades handle sub tables. group_invites are never purged (join history).
 */
@Component
@ConditionalOnProperty(name = "wishlist.purge.enabled", havingValue = "true")
public class SoftDeletePurgeJob {

    private static final Logger log = LoggerFactory.getLogger(SoftDeletePurgeJob.class);
    private static final String[] TABLES = {"wishes", "wishlists", "groups", "users"};

    private final EntityManager entityManager;

    public SoftDeletePurgeJob(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purge() {
        for (String table : TABLES) {
            int deleted = entityManager
                    .createNativeQuery("DELETE FROM " + table + " WHERE deleted_at < now() - interval '30 days'")
                    .executeUpdate();
            if (deleted > 0) {
                log.info("Purged {} rows from {}", deleted, table);
            }
        }
    }
}
