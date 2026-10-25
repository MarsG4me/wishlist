package de.marsg.wishlist.helpers;

import de.marsg.wishlist.endpoints.services.KeycloakAccountClient;
import de.marsg.wishlist.endpoints.services.UserService;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Automatic variant of scripts/purge_soft_deleted.sql (on by default, disable with
 * wishlist.purge.enabled=false). Expired users are erased at Keycloak before their row
 * goes; a failure skips that user until the next run. group_invites are never purged.
 */
@Component
@ConditionalOnProperty(name = "wishlist.purge.enabled", havingValue = "true", matchIfMissing = true)
public class SoftDeletePurgeJob {

    private static final Logger log = LoggerFactory.getLogger(SoftDeletePurgeJob.class);
    private static final String[] TABLES = {"wishes", "wishlists", "groups"};
    /** How long a soft-deleted row survives before the purge erases it. */
    public static final Duration RETENTION = Duration.ofDays(30);
    private static final String EXPIRED = "deleted_at < now() - interval '" + RETENTION.toDays() + " days'";

    private final EntityManager entityManager;
    private final KeycloakAccountClient keycloakAccountClient;
    private final UserService userService;
    private final TransactionTemplate transactionTemplate;

    public SoftDeletePurgeJob(EntityManager entityManager, KeycloakAccountClient keycloakAccountClient,
            UserService userService, TransactionTemplate transactionTemplate) {
        this.entityManager = entityManager;
        this.keycloakAccountClient = keycloakAccountClient;
        this.userService = userService;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void purge() {
        transactionTemplate.executeWithoutResult(status -> {
            for (String table : TABLES) {
                int deleted = entityManager.createNativeQuery("DELETE FROM " + table + " WHERE " + EXPIRED)
                        .executeUpdate();
                if (deleted > 0) {
                    log.info("Purged {} rows from {}", deleted, table);
                }
            }
        });
        purgeUsers();
    }

    private void purgeUsers() {
        List<?> ids = transactionTemplate.execute(status -> entityManager
                .createNativeQuery("SELECT id FROM users WHERE " + EXPIRED).getResultList());
        int purged = 0;
        for (Object id : ids == null ? List.of() : ids) {
            try {
                if (Boolean.TRUE.equals(transactionTemplate.execute(status -> purgeUser((UUID) id)))) {
                    purged++;
                }
            } catch (RuntimeException e) {
                log.warn("Purge failed for user {}; row kept for the next run", id);
            }
        }
        if (purged > 0) {
            log.info("Purged {} rows from users", purged);
        }
    }

    /** One user in one transaction: row locked and re-checked as expired, Keycloak erased, then the row. */
    private boolean purgeUser(UUID userId) {
        List<?> locked = entityManager.createNativeQuery("SELECT keycloak_id FROM users WHERE id = :id AND "
                + EXPIRED + " FOR UPDATE SKIP LOCKED").setParameter("id", userId).getResultList();
        if (locked.isEmpty()) {
            return false;
        }
        userService.releaseTies(userId);
        keycloakAccountClient.deleteUser((UUID) locked.get(0));
        return entityManager.createNativeQuery("DELETE FROM users WHERE id = :id AND " + EXPIRED)
                .setParameter("id", userId).executeUpdate() > 0;
    }
}
