package de.marsg.wishlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.marsg.wishlist.endpoints.services.KeycloakAccountClient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Full-flow integration test touching EVERY endpoint in dependency order against a
 * real Postgres 18 (Flyway schema, native uuidv7, enums, constraints). Keycloak is
 * not needed: JWTs are injected via spring-security-test, and the account client is
 * mocked. Run with: mvn verify -Pit
 *
 * The flow mirrors real usage, so a regression anywhere breaks the steps downstream —
 * exactly what it is for.
 *
 * DB selection: Testcontainers postgres:18 by default (CI). On machines where the
 * firewall blocks container port mapping, point IT_DB_URL at an existing Postgres
 * (see scripts/integration_test_local.sh).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullFlowIT {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
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

    @MockitoBean
    private KeycloakAccountClient keycloakAccountClient;

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID CAROL = UUID.randomUUID();
    private static final UUID DAVE = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();

    // ids produced along the flow
    private String aliceId;
    private String bobId;
    private String wishlistId;
    private String wishId;
    private String groupId;
    private String inviteId;
    private String inviteCode;
    private String bobMembershipId;
    private String carolMembershipId;
    private String bobClaimId;
    private String daveClaimId;

    private static RequestPostProcessor as(UUID sub, String given, String family, boolean admin) {
        List<org.springframework.security.core.GrantedAuthority> authorities = admin
                ? List.of(new SimpleGrantedAuthority("ROLE_user"), new SimpleGrantedAuthority("ROLE_admin"))
                : List.of(new SimpleGrantedAuthority("ROLE_user"));
        return jwt()
                .jwt(jwt -> jwt.subject(sub.toString())
                        .claim("given_name", given)
                        .claim("family_name", family)
                        .claim("email", given.toLowerCase() + "@it.local"))
                .authorities(authorities);
    }

    private static RequestPostProcessor alice() {
        return as(ALICE, "Alice", "Anderson", false);
    }

    private static RequestPostProcessor bob() {
        return as(BOB, "Bob", "Baker", false);
    }

    private static RequestPostProcessor carol() {
        return as(CAROL, "Carol", "Clark", false);
    }

    private static RequestPostProcessor dave() {
        return as(DAVE, "Dave", "Dent", false);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String perform(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        return mvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    // ---------- health & error shape ----------

    @Test
    @Order(1)
    void healthIsPublic() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk());
    }

    @Test
    @Order(2)
    void unauthenticatedGetsErrorDto() throws Exception {
        mvc.perform(get("/v1/users/me"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/nope").with(alice()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("ROUTE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // ---------- users ----------

    @Test
    @Order(10)
    void jitProvisioningAndOwnUser() throws Exception {
        String body = perform(get("/v1/users/me").with(alice()), 200);
        aliceId = JsonPath.read(body, "$.id");
        assertThat((String) JsonPath.read(body, "$.displayname")).isEqualTo("Alice Anderson");
        assertThat((String) JsonPath.read(body, "$.tier")).isEqualTo("FREE");
        bobId = JsonPath.read(perform(get("/v1/users/me").with(bob()), 200), "$.id");
    }

    @Test
    @Order(11)
    void updateDisplayname() throws Exception {
        mvc.perform(json(patch("/v1/users/me").with(alice()), "{\"displayname\":\"Alice A.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayname").value("Alice A."));
    }

    @Test
    @Order(12)
    void emailChangeGoesThroughKeycloakFirst() throws Exception {
        mvc.perform(json(patch("/v1/users/me/email").with(alice()), "{\"email\":\"alice.new@it.local\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice.new@it.local"));
        Mockito.verify(keycloakAccountClient).updateEmail(Mockito.anyString(), Mockito.eq("alice.new@it.local"));
    }

    @Test
    @Order(13)
    void tierChangeRequiresAdminRole() throws Exception {
        mvc.perform(json(patch("/v1/users/" + aliceId + "/tier").with(bob()), "{\"tier\":\"PREMIUM\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(json(patch("/v1/users/" + aliceId + "/tier").with(as(ADMIN, "Ada", "Admin", true)),
                        "{\"tier\":\"PREMIUM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("PREMIUM"));
    }

    @Test
    @Order(14)
    void otherUserVisibleOnlyWithCommonGroup() throws Exception {
        mvc.perform(get("/v1/users/" + bobId).with(alice()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("USER_HAS_NO_COMMON_GROUP"));
    }

    // ---------- wishlists & wishes ----------

    @Test
    @Order(20)
    void wishlistCrud() throws Exception {
        wishlistId = JsonPath.read(
                perform(json(post("/v1/wishlists").with(alice()), "{\"name\":\"Birthday\"}"), 201), "$.id");
        mvc.perform(get("/v1/wishlists").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(wishlistId));
        mvc.perform(json(patch("/v1/wishlists/" + wishlistId).with(alice()), "{\"name\":\"B-Day\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("B-Day"));
        mvc.perform(get("/v1/wishlists/" + wishlistId).with(bob()))
                .andExpect(status().isNotFound()); // not shared yet -> hidden
    }

    @Test
    @Order(21)
    void wishCrudWithLinks() throws Exception {
        String body = perform(json(post("/v1/wishes").with(alice()),
                "{\"wishlist_id\":\"" + wishlistId + "\",\"name\":\"Lego\",\"description\":\"big\","
                        + "\"quantity\":2,\"links\":[{\"url\":\"https://example.com\",\"label\":\"shop\"}]}"), 201);
        wishId = JsonPath.read(body, "$.id");
        assertThat((List<?>) JsonPath.read(body, "$.links")).hasSize(1);
        mvc.perform(get("/v1/wishes/" + wishId).with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claims").doesNotExist()); // owner view is claim-free by type
    }

    // ---------- groups, invites, memberships, shares ----------

    @Test
    @Order(30)
    void groupCrudAndInvites() throws Exception {
        String group = perform(json(post("/v1/groups").with(alice()),
                "{\"name\":\"Family\",\"description\":\"fam\"}"), 201);
        groupId = JsonPath.read(group, "$.id");
        mvc.perform(get("/v1/groups").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(groupId));
        mvc.perform(json(patch("/v1/groups/" + groupId).with(alice()), "{\"name\":\"Fam\",\"description\":null}"))
                .andExpect(status().isOk());

        String invite = perform(json(post("/v1/groups/" + groupId + "/invites").with(alice()),
                "{\"max_uses\":3}"), 201);
        inviteId = JsonPath.read(invite, "$.id");
        inviteCode = JsonPath.read(invite, "$.code");
        mvc.perform(get("/v1/groups/" + groupId + "/invites").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].use_count").value(0));
        // non-admin bob cannot see invites
        mvc.perform(get("/v1/groups/" + groupId + "/invites").with(bob()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(31)
    void joiningViaInviteCode() throws Exception {
        bobMembershipId = JsonPath.read(perform(json(post("/v1/memberships/join").with(bob()),
                "{\"code\":\"" + inviteCode + "\"}"), 201), "$.id");
        carolMembershipId = JsonPath.read(perform(json(post("/v1/memberships/join").with(carol()),
                "{\"code\":\"" + inviteCode + "\"}"), 201), "$.id");
        perform(json(post("/v1/memberships/join").with(dave()), "{\"code\":\"" + inviteCode + "\"}"), 201);
        // 3 of 3 uses consumed -> exhausted for a 5th user; bob rejoin -> already member first
        mvc.perform(json(post("/v1/memberships/join").with(bob()), "{\"code\":\"" + inviteCode + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("ALREADY_MEMBER"));
        mvc.perform(get("/v1/memberships").param("group_id", groupId).with(bob()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
        // membership admin toggle by owner; owner row immutable
        mvc.perform(json(patch("/v1/memberships/" + bobMembershipId).with(alice()), "{\"is_admin\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_admin").value(true));
        // common group now exists -> user lookup works
        mvc.perform(get("/v1/users/" + bobId).with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayname").value("Bob Baker"));
    }

    @Test
    @Order(32)
    void invitesGetRevokedButStayAsHistory() throws Exception {
        mvc.perform(delete("/v1/groups/" + groupId + "/invites/" + inviteId).with(alice()))
                .andExpect(status().isNoContent());
        mvc.perform(json(post("/v1/memberships/join").with(as(UUID.randomUUID(), "Eve", "Extra", false)),
                        "{\"code\":\"" + inviteCode + "\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error_code").value("INVITE_REVOKED"));
        mvc.perform(get("/v1/groups/" + groupId + "/invites").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].revoked_at").isNotEmpty());
    }

    @Test
    @Order(33)
    void sharingWishlistIntoGroup() throws Exception {
        mvc.perform(json(post("/v1/groups/" + groupId + "/shares").with(alice()),
                        "{\"wishlist_id\":\"" + wishlistId + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/v1/groups/" + groupId + "/shares").with(bob()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].wishlist_id").value(wishlistId));
        // duplicate share rejected
        mvc.perform(json(post("/v1/groups/" + groupId + "/shares").with(alice()),
                        "{\"wishlist_id\":\"" + wishlistId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("WISHLIST_ALREADY_SHARED"));
        // bob (member) can now view the wishlist
        mvc.perform(get("/v1/wishlists/" + wishlistId).with(bob()))
                .andExpect(status().isOk());
    }

    // ---------- claims ----------

    @Test
    @Order(40)
    void claimingCoClaimingAndCapacity() throws Exception {
        // owner cannot claim own wish
        mvc.perform(json(post("/v1/claims").with(alice()), "{\"wish_id\":\"" + wishId + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("CANNOT_CLAIM_OWN_WISH"));
        bobClaimId = JsonPath.read(perform(json(post("/v1/claims").with(bob()),
                "{\"wish_id\":\"" + wishId + "\"}"), 201), "$.claim_id");
        // carol pools money on bob's claim
        mvc.perform(post("/v1/claims/" + bobClaimId + "/participants").with(carol()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.participants.length()").value(2));
        // carol cannot also claim independently
        mvc.perform(json(post("/v1/claims").with(carol()), "{\"wish_id\":\"" + wishId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("ALREADY_CLAIMED"));
        // dave takes unit 2/2
        daveClaimId = JsonPath.read(perform(json(post("/v1/claims").with(dave()),
                "{\"wish_id\":\"" + wishId + "\"}"), 201), "$.claim_id");
        // carol leaves the co-claim and tries her own -> capacity full
        mvc.perform(delete("/v1/claims/" + bobClaimId + "/participants/me").with(carol()))
                .andExpect(status().isNoContent());
        mvc.perform(json(post("/v1/claims").with(carol()), "{\"wish_id\":\"" + wishId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("WISH_FULLY_CLAIMED"));
    }

    @Test
    @Order(41)
    void ownerViewStaysClaimFreeSharedViewShowsClaims() throws Exception {
        String ownerView = perform(get("/v1/wishes").param("wishlist_id", wishlistId).with(alice()), 200);
        assertThat(ownerView).doesNotContain("claim", "participants", "remaining");
        String sharedView = perform(get("/v1/wishes").param("wishlist_id", wishlistId).with(bob()), 200);
        assertThat((Integer) JsonPath.read(sharedView, "$[0].remaining")).isEqualTo(0);
        assertThat((List<?>) JsonPath.read(sharedView, "$[0].claims")).hasSize(2);
        // my-claims overview
        mvc.perform(get("/v1/claims").with(bob()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].wish_id").value(wishId));
    }

    @Test
    @Order(42)
    void quantityReductionGrandfathersClaims() throws Exception {
        mvc.perform(json(patch("/v1/wishes/" + wishId).with(alice()),
                        "{\"name\":\"Lego\",\"quantity\":1,\"links\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existing_claims_affected").value(true));
    }

    @Test
    @Order(43)
    void deletingClaimedWishNeedsConfirmation() throws Exception {
        mvc.perform(delete("/v1/wishes/" + wishId).with(alice()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("WISH_HAS_CLAIMS"));
        mvc.perform(delete("/v1/wishes/" + wishId).param("confirm", "true").with(alice()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/v1/wishes/" + wishId).with(alice()))
                .andExpect(status().isNotFound());
        // claims overview no longer lists the deleted wish
        String claims = perform(get("/v1/claims").with(bob()), 200);
        assertThat(claims).doesNotContain(wishId);
    }

    // ---------- leaving, unsharing, deleting ----------

    @Test
    @Order(50)
    void leavingAndRemovingMembers() throws Exception {
        // dave leaves his claim first (claim disappears with last participant)
        mvc.perform(delete("/v1/claims/" + daveClaimId + "/participants/me").with(dave()))
                .andExpect(status().isNoContent());
        // carol leaves the group herself
        mvc.perform(delete("/v1/memberships/" + carolMembershipId).with(carol()))
                .andExpect(status().isNoContent());
        // owner cannot leave
        String memberships = perform(get("/v1/memberships").param("group_id", groupId).with(alice()), 200);
        String ownerMembershipId = JsonPath.read(memberships,
                "$[?(@.is_owner == true)].id").toString().replaceAll("[\\[\\]\"]", "");
        mvc.perform(delete("/v1/memberships/" + ownerMembershipId).with(alice()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("OWNER_CANNOT_LEAVE"));
    }

    @Test
    @Order(51)
    void unshareAndSoftDeleteEverything() throws Exception {
        mvc.perform(delete("/v1/groups/" + groupId + "/shares/" + wishlistId).with(alice()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/v1/groups/" + groupId).with(bob()))
                .andExpect(status().isForbidden()); // admin but not owner
        mvc.perform(delete("/v1/groups/" + groupId).with(alice()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/v1/wishlists/" + wishlistId).with(alice()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/v1/wishlists").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @Order(52)
    void accountSoftDeleteAndRestoreOnRelogin() throws Exception {
        mvc.perform(delete("/v1/users/me").with(dave()))
                .andExpect(status().isNoContent());
        // logging in again within the purge window restores the account with its data
        mvc.perform(get("/v1/users/me").with(dave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayname").value("Dave Dent"));
    }
}
