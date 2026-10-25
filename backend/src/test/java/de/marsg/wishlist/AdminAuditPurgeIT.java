package de.marsg.wishlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.marsg.wishlist.endpoints.services.AdminAuditService;
import de.marsg.wishlist.endpoints.services.KeycloakAccountClient;
import de.marsg.wishlist.helpers.SoftDeletePurgeJob;
import de.marsg.wishlist.jpa.entities.AdminAuditAction;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Real-Postgres checks for the purge job (SKIP LOCKED, audit retention), the audit
 * table and the onboarding counts. DB selection as in {@link FullFlowIT} (IT_DB_URL or Testcontainers).
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminAuditPurgeIT {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("wishlist.link-safety.blocklist.enabled", () -> "false");
        String externalUrl = System.getenv("IT_DB_URL");
        if (externalUrl != null) {
            registry.add("spring.datasource.url", () -> externalUrl);
            registry.add("spring.datasource.username",
                    () -> System.getenv().getOrDefault("IT_DB_USER", "wishlist"));
            registry.add("spring.datasource.password",
                    () -> System.getenv().getOrDefault("IT_DB_PASSWORD", "wishlist"));
        } else {
            PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");
            postgres.start();
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SoftDeletePurgeJob purgeJob;
    @Autowired
    private AdminAuditService auditService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private KeycloakAccountClient keycloakAccountClient;

    private final UUID auditAdmin = UUID.randomUUID();

    private UUID insertUser(UUID keycloakId, String email, boolean expired) {
        return jdbc.queryForObject("INSERT INTO users (keycloak_id, displayname, email, deleted_at) VALUES (?, 'T', ?, "
                + (expired ? "now() - interval '31 days'" : "NULL") + ") RETURNING id",
                UUID.class, keycloakId, email);
    }

    @AfterAll
    void cleanUp() {
        jdbc.update("DELETE FROM users WHERE email LIKE '%@audit-it.local'");
        jdbc.update("DELETE FROM admin_audit WHERE admin_users_id = ?", auditAdmin);
    }

    @Test
    void purgeSkipsAUserRowLockedByAnotherTransaction() throws Exception {
        UUID keycloakId = UUID.randomUUID();
        UUID userId = insertUser(keycloakId, "locked@audit-it.local", true);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = Thread.ofPlatform().start(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbc.queryForList("SELECT id FROM users WHERE id = ? FOR UPDATE", userId);
            locked.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        purgeJob.purge();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, userId)).isEqualTo(1);
        verify(keycloakAccountClient, never()).deleteUser(keycloakId);

        release.countDown();
        holder.join();
        purgeJob.purge();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, userId)).isZero();
        verify(keycloakAccountClient).deleteUser(keycloakId);
    }

    @Test
    void auditRowsAreWrittenAndOnlyOldOnesPurged() {
        transactionTemplate.executeWithoutResult(status -> {
            auditService.record(auditAdmin, AdminAuditAction.USER_SEARCH, null,
                    Map.of("term_length", 5, "include_deleted", false, "matches", 2));
            auditService.record(auditAdmin, AdminAuditAction.TIER_CHANGE, UUID.randomUUID(),
                    Map.of("tier", "PREMIUM"));
        });
        jdbc.update("INSERT INTO admin_audit (id, admin_users_id, action) VALUES (uuidv7(interval '-13 months'), "
                + "?, 'USER_EXPORT')", auditAdmin);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_audit WHERE admin_users_id = ?", Integer.class,
                auditAdmin)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT detail->>'matches' FROM admin_audit WHERE admin_users_id = ? "
                + "AND action = 'USER_SEARCH'", String.class, auditAdmin)).isEqualTo("2");

        purgeJob.purge();

        assertThat(jdbc.queryForList("SELECT action::text FROM admin_audit WHERE admin_users_id = ?", String.class,
                auditAdmin)).containsExactlyInAnyOrder("USER_SEARCH", "TIER_CHANGE");
        verify(keycloakAccountClient, never()).deleteUser(any(UUID.class));
    }

    @Test
    void onboardingCountsIgnoreSoftDeletedRows() throws Exception {
        UUID keycloakId = UUID.randomUUID();
        UUID userId = insertUser(keycloakId, "onboard@audit-it.local", false);
        UUID liveList = jdbc.queryForObject("INSERT INTO wishlists (users_id, name) VALUES (?, 'a') RETURNING id",
                UUID.class, userId);
        UUID deadList = jdbc.queryForObject(
                "INSERT INTO wishlists (users_id, name, deleted_at) VALUES (?, 'b', now()) RETURNING id", UUID.class,
                userId);
        jdbc.update("INSERT INTO wishes (wishlists_id, name) VALUES (?, 'w1'), (?, 'w2'), (?, 'in dead list')",
                liveList, liveList, deadList);
        jdbc.update("INSERT INTO wishes (wishlists_id, name, deleted_at) VALUES (?, 'gone', now())", liveList);
        UUID liveGroup = jdbc.queryForObject("INSERT INTO groups (users_id, name) VALUES (?, 'g') RETURNING id",
                UUID.class, userId);
        UUID deadGroup = jdbc.queryForObject(
                "INSERT INTO groups (users_id, name, deleted_at) VALUES (?, 'g2', now()) RETURNING id", UUID.class,
                userId);
        jdbc.update("INSERT INTO group_memberships (groups_id, users_id, is_admin) VALUES (?, ?, true), (?, ?, true)",
                liveGroup, userId, deadGroup, userId);

        mvc.perform(get("/v1/users/me/onboarding").with(jwt()
                        .jwt(j -> j.subject(keycloakId.toString()).claim("email", "onboard@audit-it.local"))
                        .authorities(new SimpleGrantedAuthority("ROLE_user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wishlist_total").value(1))
                .andExpect(jsonPath("$.wish_total").value(2))
                .andExpect(jsonPath("$.group_total").value(1));
    }
}
