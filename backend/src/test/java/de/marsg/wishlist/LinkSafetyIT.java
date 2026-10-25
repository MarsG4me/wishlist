package de.marsg.wishlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.marsg.wishlist.endpoints.services.KeycloakAccountClient;
import de.marsg.wishlist.endpoints.services.LinkRescanService;
import de.marsg.wishlist.endpoints.services.WebRiskClient;
import de.marsg.wishlist.jpa.repositories.WebRiskUsageRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Link safety end to end on real Postgres: checks at save time, the host verdict cache,
 * reports, admin decisions, rescans and what survives an account purge. Web Risk is stubbed.
 * DB selection as in {@link FullFlowIT} (IT_DB_URL or Testcontainers).
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LinkSafetyIT {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("wishlist.link-safety.blocklist.enabled", () -> "false");
        registry.add("wishlist.purge.enabled", () -> "false");
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
    private LinkRescanService rescanService;
    @Autowired
    private WebRiskUsageRepository usageRepository;

    @MockitoBean
    private KeycloakAccountClient keycloakAccountClient;
    @MockitoBean
    private WebRiskClient webRisk;

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID VIEWER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();

    private String ownerId;
    private String viewerId;
    private String adminId;
    private String wishlistId;
    private String wishId;
    private String linkId;
    private String reportId;
    private String blockedDomainId;

    private static RequestPostProcessor as(UUID sub, String name, boolean admin) {
        List<GrantedAuthority> authorities = admin
                ? List.of(new SimpleGrantedAuthority("ROLE_user"), new SimpleGrantedAuthority("ROLE_admin"))
                : List.of(new SimpleGrantedAuthority("ROLE_user"));
        return jwt().jwt(j -> j.subject(sub.toString()).claim("given_name", name).claim("family_name", "Links")
                .claim("email", name.toLowerCase() + "@links-it.local")).authorities(authorities);
    }

    private static RequestPostProcessor owner() {
        return as(OWNER, "Olga", false);
    }

    private static RequestPostProcessor viewer() {
        return as(VIEWER, "Viktor", false);
    }

    private static RequestPostProcessor stranger() {
        return as(STRANGER, "Sven", false);
    }

    private static RequestPostProcessor admin() {
        return as(ADMIN, "Adam", true);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String perform(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        return mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse()
                .getContentAsString();
    }

    private String wishBody(String... urls) {
        StringBuilder links = new StringBuilder();
        for (String url : urls) {
            links.append(links.isEmpty() ? "" : ",").append("{\"url\":\"").append(url).append("\"}");
        }
        return "{\"wishlist_id\":\"" + wishlistId + "\",\"name\":\"Bricks\",\"quantity\":1,\"links\":[" + links + "]}";
    }

    private int auditRows(String action) {
        return jdbc.queryForObject("SELECT count(*) FROM admin_audit WHERE action = CAST(? AS admin_audit_action) "
                + "AND admin_users_id = ?", Integer.class, action, UUID.fromString(adminId));
    }

    @BeforeEach
    void stubs() {
        when(keycloakAccountClient.userExists(org.mockito.ArgumentMatchers.any(UUID.class))).thenReturn(true);
        when(webRisk.enabled()).thenReturn(true);
        when(webRisk.isUnsafe(anyString())).thenReturn(false);
        when(webRisk.isUnsafe("evil.example")).thenReturn(true);
    }

    @AfterAll
    void cleanUp() {
        jdbc.update("DELETE FROM link_reports");
        jdbc.update("DELETE FROM blocked_domains");
        jdbc.update("DELETE FROM link_host_checks WHERE host LIKE '%.example'");
        jdbc.update("DELETE FROM web_risk_usage");
        jdbc.update("DELETE FROM users WHERE email LIKE '%@links-it.local'");
        jdbc.update("DELETE FROM admin_audit WHERE admin_users_id = ?", UUID.fromString(adminId));
    }

    @Test
    @Order(1)
    void migrationValidatedTheSchemeConstraint() {
        assertThat(jdbc.queryForObject("SELECT convalidated FROM pg_constraint WHERE conname = "
                + "'chk_wish_links_url_scheme'", Boolean.class)).isTrue();
    }

    @Test
    @Order(2)
    void setUpUsersGroupAndSharedWishlist() throws Exception {
        ownerId = JsonPath.read(perform(get("/v1/users/me").with(owner()), 200), "$.id");
        viewerId = JsonPath.read(perform(get("/v1/users/me").with(viewer()), 200), "$.id");
        perform(get("/v1/users/me").with(stranger()), 200);
        adminId = JsonPath.read(perform(get("/v1/users/me").with(admin()), 200), "$.id");
        wishlistId = JsonPath.read(perform(json(post("/v1/wishlists").with(owner()), "{\"name\":\"Gifts\"}"), 201), "$.id");
        String groupId = JsonPath.read(perform(json(post("/v1/groups").with(owner()),
                "{\"name\":\"Fam\",\"description\":null}"), 201), "$.id");
        String code = JsonPath.read(perform(json(post("/v1/groups/" + groupId + "/invites").with(owner()),
                "{\"max_uses\":2}"), 201), "$.code");
        perform(json(post("/v1/memberships/join").with(viewer()), "{\"code\":\"" + code + "\"}"), 201);
        perform(json(post("/v1/groups/" + groupId + "/shares").with(owner()),
                "{\"wishlist_id\":\"" + wishlistId + "\"}"), 201);
    }

    @Test
    @Order(10)
    void shortenerAndUnsafeLinksFailWithTheirIndex() throws Exception {
        mvc.perform(json(post("/v1/wishes").with(owner()), wishBody("https://other.example/a", "https://bit.ly/x")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("LINK_SHORTENER_NOT_ALLOWED"))
                .andExpect(jsonPath("$.link_index").value(1));
        mvc.perform(json(post("/v1/wishes").with(owner()), wishBody("https://other.example/a", "https://evil.example/", "ftp://nope")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("LINK_BLOCKED"))
                .andExpect(jsonPath("$.link_index").value(1));
        mvc.perform(json(post("/v1/wishes").with(owner()), wishBody("ftp://nope", "https://evil.example/")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_LINK_URL"))
                .andExpect(jsonPath("$.link_index").value(0));
        assertThat(jdbc.queryForObject("SELECT CAST(verdict AS text) FROM link_host_checks WHERE host = 'evil.example'",
                String.class)).isEqualTo("UNSAFE");
    }

    @Test
    @Order(11)
    void aStoredVerdictSkipsWebRiskForTheSameHost() throws Exception {
        String body = perform(json(post("/v1/wishes").with(owner()), wishBody("https://good.example/p")), 201);
        wishId = JsonPath.read(body, "$.id");
        linkId = JsonPath.read(body, "$.links[0].id");
        assertThat((Boolean) JsonPath.read(body, "$.links[0].blocked")).isFalse();
        perform(json(post("/v1/wishes").with(owner()), wishBody("https://good.example/q")), 201);

        verify(webRisk, times(1)).isUnsafe("good.example");
        assertThat(jdbc.queryForObject("SELECT CAST(verdict AS text) FROM link_host_checks WHERE host = 'good.example'",
                String.class)).isEqualTo("SAFE");
        assertThat(jdbc.queryForObject("SELECT host FROM wish_links WHERE id = ?", String.class, UUID.fromString(linkId)))
                .isEqualTo("good.example");
    }

    @Test
    @Order(12)
    void viewersSeeTheLinkWithoutBlockedFields() throws Exception {
        mvc.perform(get("/v1/wishes/" + wishId).with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links[0].id").value(linkId))
                .andExpect(jsonPath("$.links[0].blocked").doesNotExist())
                .andExpect(jsonPath("$.links[0].blocked_by").doesNotExist());
    }

    @Test
    @Order(13)
    void editingAWishKeepsTheIdOfAnUnchangedLink() throws Exception {
        mvc.perform(json(patch("/v1/wishes/" + wishId).with(owner()),
                "{\"name\":\"Bricks\",\"quantity\":1,\"links\":[{\"url\":\"https://good.example/p\",\"label\":\"Shop\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wish.links[0].id").value(linkId))
                .andExpect(jsonPath("$.wish.links[0].label").value("Shop"));
    }

    @Test
    @Order(20)
    void reportRules() throws Exception {
        String report = "{\"reason\":\"SCAM\",\"comment\":\"fake shop\"}";
        perform(json(post("/v1/links/" + linkId + "/reports").with(viewer()), report), 204);
        mvc.perform(json(post("/v1/links/" + linkId + "/reports").with(viewer()), report))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("LINK_ALREADY_REPORTED"));
        mvc.perform(json(post("/v1/links/" + linkId + "/reports").with(owner()), report))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CANNOT_REPORT_OWN_LINK"));
        mvc.perform(json(post("/v1/links/" + linkId + "/reports").with(stranger()), report))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value("LINK_NOT_FOUND"));
        mvc.perform(json(post("/v1/links/" + linkId + "/reports").with(viewer()), "{\"reason\":\"NOPE\"}"))
                .andExpect(status().isBadRequest());
        // a pending report keeps its link when the owner edits the wish again
        perform(json(patch("/v1/wishes/" + wishId).with(owner()),
                "{\"name\":\"Bricks\",\"quantity\":1,\"links\":[{\"url\":\"https://good.example/p\",\"label\":\"Again\"}]}"), 200);
        assertThat(jdbc.queryForObject("SELECT wish_links_id FROM link_reports", UUID.class))
                .isEqualTo(UUID.fromString(linkId));
    }

    @Test
    @Order(21)
    void adminQueueIsAdminOnlyAndFilledFromTheJoins() throws Exception {
        mvc.perform(get("/v1/admin/link-reports").with(viewer())).andExpect(status().isForbidden());
        String body = perform(get("/v1/admin/link-reports").param("status", "OPEN").with(admin()), 200);
        assertThat((Integer) JsonPath.read(body, "$.total")).isEqualTo(1);
        reportId = JsonPath.read(body, "$.items[0].id");
        assertThat((String) JsonPath.read(body, "$.items[0].url")).isEqualTo("https://good.example/p");
        assertThat((String) JsonPath.read(body, "$.items[0].host")).isEqualTo("good.example");
        assertThat((String) JsonPath.read(body, "$.items[0].wish_title")).isEqualTo("Bricks");
        assertThat((String) JsonPath.read(body, "$.items[0].owner_displayname")).isEqualTo("Olga Links");
        assertThat((String) JsonPath.read(body, "$.items[0].reporter_displayname")).isEqualTo("Viktor Links");
        assertThat((String) JsonPath.read(body, "$.items[0].reason")).isEqualTo("SCAM");
        mvc.perform(get("/v1/admin/link-reports").param("status", "DISMISSED").with(admin()))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/v1/admin/link-reports/" + reportId + "/authority-report").with(admin()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("REPORT_NOT_ESCALATED"));
    }

    @Test
    @Order(22)
    void escalatingNeedsABlockActionThenBlockDomainHidesTheLink() throws Exception {
        mvc.perform(json(post("/v1/admin/link-reports/" + reportId + "/decision").with(admin()),
                "{\"action\":\"DISMISS\",\"escalate\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));

        mvc.perform(json(post("/v1/admin/link-reports/" + reportId + "/decision").with(admin()),
                "{\"action\":\"BLOCK_DOMAIN\",\"escalate\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESCALATED"))
                .andExpect(jsonPath("$.blocked_by").value("ADMIN_DOMAIN"))
                .andExpect(jsonPath("$.decided_at").isNotEmpty());

        mvc.perform(json(post("/v1/admin/link-reports/" + reportId + "/decision").with(admin()),
                "{\"action\":\"DISMISS\",\"escalate\":false}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("LINK_REPORT_ALREADY_DECIDED"));
        assertThat(auditRows("LINK_REPORT_DECISION")).isEqualTo(1);
    }

    @Test
    @Order(23)
    void ownerSeesTheFlagViewersLoseTheLinkAndResubmittingIsRefused() throws Exception {
        mvc.perform(get("/v1/wishes/" + wishId).with(owner()))
                .andExpect(jsonPath("$.links[0].blocked").value(true))
                .andExpect(jsonPath("$.links[0].blocked_by").value("ADMIN_DOMAIN"));
        mvc.perform(get("/v1/wishes/" + wishId).with(viewer())).andExpect(jsonPath("$.links").isEmpty());
        mvc.perform(json(patch("/v1/wishes/" + wishId).with(owner()),
                "{\"name\":\"Bricks\",\"quantity\":1,\"links\":[{\"url\":\"https://good.example/p\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("LINK_BLOCKED"))
                .andExpect(jsonPath("$.link_index").value(0));
        mvc.perform(json(post("/v1/wishes").with(owner()), wishBody("https://www.good.example/other")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("LINK_BLOCKED"));
    }

    @Test
    @Order(24)
    void authorityReportDownloadsAreAttachmentsAndAudited() throws Exception {
        mvc.perform(get("/v1/admin/link-reports/" + reportId + "/authority-report").param("format", "json")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
                .andExpect(jsonPath("$.evidence.url").value("https://good.example/p"))
                .andExpect(jsonPath("$.evidence.reason").value("SCAM"))
                .andExpect(jsonPath("$.evidence.owner_displayname").value("Olga Links"));
        String html = perform(get("/v1/admin/link-reports/" + reportId + "/authority-report").param("format", "html")
                .with(admin()), 200);
        assertThat(html).contains("<table>", "good.example");
        mvc.perform(get("/v1/admin/link-reports/" + reportId + "/authority-report").param("format", "xml")
                .with(admin())).andExpect(status().isBadRequest());
        assertThat(auditRows("LINK_REPORT_EXPORT")).isEqualTo(2);
    }

    @Test
    @Order(25)
    void reporterExportListsTheirReport() throws Exception {
        mvc.perform(get("/v1/users/me/export").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.link_reports[0].reason").value("SCAM"))
                .andExpect(jsonPath("$.link_reports[0].url").value("https://good.example/p"));
    }

    @Test
    @Order(26)
    void evidenceOutlivesTheOwnerAccount() throws Exception {
        jdbc.update("DELETE FROM users WHERE id = ?", UUID.fromString(ownerId));

        String body = perform(get("/v1/admin/link-reports").with(admin()), 200);
        assertThat((Integer) JsonPath.read(body, "$.total")).isEqualTo(1);
        assertThat((Object) JsonPath.read(body, "$.items[0].link_id")).isNull();
        assertThat((String) JsonPath.read(body, "$.items[0].url")).isEqualTo("https://good.example/p");
        assertThat((String) JsonPath.read(body, "$.items[0].owner_displayname")).isEqualTo("Olga Links");
        mvc.perform(get("/v1/admin/link-reports/" + reportId + "/authority-report").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.evidence.owner_email").value("olga@links-it.local"));
        mvc.perform(get("/v1/users/me/export").with(viewer()))
                .andExpect(jsonPath("$.link_reports[0].url").doesNotExist());
    }

    @Test
    @Order(30)
    void blockedDomainsListAndUnblock() throws Exception {
        String body = perform(get("/v1/admin/blocked-domains").with(admin()), 200);
        assertThat((Integer) JsonPath.read(body, "$.total")).isEqualTo(1);
        assertThat((String) JsonPath.read(body, "$.items[0].host")).isEqualTo("good.example");
        assertThat((String) JsonPath.read(body, "$.items[0].link_report_id")).isEqualTo(reportId);
        assertThat((String) JsonPath.read(body, "$.items[0].blocked_at")).isNotEmpty();
        blockedDomainId = JsonPath.read(body, "$.items[0].id");

        perform(delete("/v1/admin/blocked-domains/" + blockedDomainId).with(admin()), 204);
        mvc.perform(delete("/v1/admin/blocked-domains/" + blockedDomainId).with(admin()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value("BLOCKED_DOMAIN_NOT_FOUND"));
        mvc.perform(get("/v1/admin/blocked-domains").with(admin())).andExpect(jsonPath("$.total").value(0));
        assertThat(auditRows("DOMAIN_UNBLOCK")).isEqualTo(1);
    }

    @Test
    @Order(40)
    void rescanBackfillsHostsAndSetsAndClearsAutomaticFlagsOnly() throws Exception {
        UUID lister = UUID.fromString(viewerId);
        UUID list = jdbc.queryForObject("INSERT INTO wishlists (users_id, name) VALUES (?, 'r') RETURNING id", UUID.class, lister);
        UUID wish = jdbc.queryForObject("INSERT INTO wishes (wishlists_id, name) VALUES (?, 'w') RETURNING id", UUID.class, list);
        UUID link = jdbc.queryForObject("INSERT INTO wish_links (wishes_id, url) VALUES (?, 'https://Rescan.example/x') "
                + "RETURNING id", UUID.class, wish);
        jdbc.update("INSERT INTO link_host_checks (host, verdict) VALUES ('rescan.example', 'UNSAFE')");

        rescanService.rescanAll();
        assertThat(flag(link)).isEqualTo("WEB_RISK");
        assertThat(jdbc.queryForObject("SELECT host FROM wish_links WHERE id = ?", String.class, link))
                .isEqualTo("rescan.example");

        jdbc.update("UPDATE link_host_checks SET verdict = 'SAFE' WHERE host = 'rescan.example'");
        rescanService.rescanAll();
        assertThat(flag(link)).isNull();

        jdbc.update("UPDATE wish_links SET blocked_by = 'ADMIN' WHERE id = ?", link);
        rescanService.rescanAll();
        assertThat(flag(link)).isEqualTo("ADMIN");

        UUID domainLink = jdbc.queryForObject("INSERT INTO wish_links (wishes_id, url, host, blocked_by) VALUES "
                + "(?, 'https://rescan.example/y', 'rescan.example', 'ADMIN_DOMAIN') RETURNING id", UUID.class, wish);
        rescanService.rescanAll();
        assertThat(flag(domainLink)).isEqualTo("ADMIN_DOMAIN");
        rescanService.rescanDomain("rescan.example", true);
        assertThat(flag(domainLink)).isNull();
        assertThat(flag(link)).isEqualTo("ADMIN");

        perform(post("/v1/admin/links/" + link + "/unblock").with(admin()), 204);
        assertThat(flag(link)).isNull();
        assertThat(auditRows("LINK_UNBLOCK")).isEqualTo(1);
        mvc.perform(post("/v1/admin/links/" + UUID.randomUUID() + "/unblock").with(admin()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value("LINK_NOT_FOUND"));
    }

    private String flag(UUID link) {
        return jdbc.queryForObject("SELECT CAST(blocked_by AS text) FROM wish_links WHERE id = ?", String.class, link);
    }

    @Test
    @Order(45)
    void anAdminBlockedUrlIsRefusedInEveryOtherWishToo() throws Exception {
        UUID list = jdbc.queryForObject("INSERT INTO wishlists (users_id, name) VALUES (?, 'x') RETURNING id", UUID.class,
                UUID.fromString(viewerId));
        UUID wish = jdbc.queryForObject("INSERT INTO wishes (wishlists_id, name) VALUES (?, 'w') RETURNING id", UUID.class, list);
        jdbc.update("INSERT INTO wish_links (wishes_id, url, host, blocked_by) VALUES "
                + "(?, 'https://elsewhere.example/blocked', 'elsewhere.example', 'ADMIN')", wish);

        mvc.perform(json(post("/v1/wishes").with(viewer()), "{\"wishlist_id\":\"" + list + "\",\"name\":\"B\","
                + "\"quantity\":1,\"links\":[{\"url\":\"https://other.example/a\"},"
                + "{\"url\":\"https://elsewhere.example/blocked\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("LINK_BLOCKED"))
                .andExpect(jsonPath("$.link_index").value(1));
    }

    @Test
    @Order(50)
    void monthlyBudgetStopsReservingAtTheLimit() {
        jdbc.update("DELETE FROM web_risk_usage");

        assertThat(usageRepository.tryReserve(2)).isEqualTo(1);
        assertThat(usageRepository.tryReserve(2)).isEqualTo(1);
        assertThat(usageRepository.tryReserve(2)).isZero();
        assertThat(usageRepository.findCallsThisMonth()).contains(2);
    }
}
