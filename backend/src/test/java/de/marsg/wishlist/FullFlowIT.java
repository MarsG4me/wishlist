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
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.ActiveProfiles;
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
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullFlowIT {

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
    private String unlimitedWishId;
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

    @BeforeEach
    void keycloakKnowsEveryone() {
        Mockito.when(keycloakAccountClient.userExists(Mockito.any(UUID.class))).thenReturn(true);
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
        // no locale claim in the token -> English until the user picks a language
        assertThat((String) JsonPath.read(body, "$.locale")).isEqualTo("EN");
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
        mvc.perform(json(patch("/v1/users/me/email").with(alice()), "{\"email\":\"alice.new@it.local\",\"current_password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice.new@it.local"));
        Mockito.verify(keycloakAccountClient).verifyCurrentPassword(Mockito.anyString(), Mockito.eq("pw"));
        Mockito.verify(keycloakAccountClient).updateEmail(Mockito.any(UUID.class), Mockito.eq("alice.new@it.local"));
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
    @Order(13)
    void secondAccountWithTakenEmailIsRejected() throws Exception {
        // same address, different identity provider subject -> reactivate instead of duplicate
        mvc.perform(get("/v1/users/me").with(as(UUID.randomUUID(), "Bob", "Twin", false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @Order(14)
    void otherUserVisibleOnlyWithCommonGroup() throws Exception {
        // answered exactly like an unknown id, so the endpoint cannot be used to probe
        // which user ids exist
        mvc.perform(get("/v1/users/" + bobId).with(alice()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("USER_NOT_FOUND"));
        mvc.perform(get("/v1/users/" + UUID.randomUUID()).with(alice()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("USER_NOT_FOUND"));
    }

    @Test
    @Order(15)
    void adminEndpointsRequireRoleAndReturnCountsOnly() throws Exception {
        mvc.perform(get("/v1/admin/stats").with(bob()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/admin/users").param("email", "alice").with(bob()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/admin/stats").with(as(ADMIN, "Ada", "Admin", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_count").isNumber())
                .andExpect(jsonPath("$.wishlist_count").isNumber())
                .andExpect(jsonPath("$.wish_count").isNumber());
        mvc.perform(get("/v1/admin/users").param("email", "alice.new").with(as(ADMIN, "Ada", "Admin", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].email").value("alice.new@it.local"))
                .andExpect(jsonPath("$.items[0].tier").value("PREMIUM"));
    }

    // ---------- interface language ----------

    @Test
    @Order(16)
    void languageChangeIsStoredHereAndMirroredIntoKeycloak() throws Exception {
        mvc.perform(json(patch("/v1/users/me/locale").with(alice()), "{\"locale\":\"DE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("DE"));
        Mockito.verify(keycloakAccountClient).updateLocale(Mockito.any(UUID.class), Mockito.eq("de"));
        // the change is what the next page load reads, no Keycloak round trip involved
        mvc.perform(get("/v1/users/me").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("DE"));
    }

    @Test
    @Order(17)
    void unshippedLanguageIsRejected() throws Exception {
        mvc.perform(json(patch("/v1/users/me/locale").with(alice()), "{\"locale\":\"FR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("MISSING_REQUEST_BODY"));
        mvc.perform(json(patch("/v1/users/me/locale").with(alice()), "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));
    }

    @Test
    @Order(18)
    void languageStillChangesWhenKeycloakIsDown() throws Exception {
        Mockito.doThrow(new ApiException(ErrorCode.KEYCLOAK_UPDATE_FAILED))
                .when(keycloakAccountClient).updateLocale(Mockito.any(UUID.class), Mockito.eq("en"));
        mvc.perform(json(patch("/v1/users/me/locale").with(alice()), "{\"locale\":\"EN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("EN"));
        Mockito.doNothing().when(keycloakAccountClient).updateLocale(Mockito.any(UUID.class), Mockito.anyString());
    }

    @Test
    @Order(19)
    void externalProviderAccountKeepsTheLanguageSetting() throws Exception {
        // email and password are refused for these accounts; the language belongs to us
        RequestPostProcessor google = jwt()
                .jwt(jwt -> jwt.subject(UUID.randomUUID().toString())
                        .claim("given_name", "Greta")
                        .claim("family_name", "Green")
                        .claim("email", "greta@it.local")
                        .claim("locale", "de-DE")
                        .claim("identity_provider", "google"))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_user")));
        // the Keycloak locale claim seeds the account on provisioning
        mvc.perform(get("/v1/users/me").with(google))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("DE"));
        mvc.perform(json(patch("/v1/users/me/email").with(google), "{\"email\":\"greta2@it.local\",\"current_password\":\"pw\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("ACCOUNT_MANAGED_EXTERNALLY"));
        mvc.perform(json(patch("/v1/users/me/locale").with(google), "{\"locale\":\"EN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("EN"));
    }

    // ---------- wishlists & wishes ----------

    @Test
    @Order(20)
    void wishlistCrud() throws Exception {
        wishlistId = JsonPath.read(
                perform(json(post("/v1/wishlists").with(alice()), "{\"name\":\"Birthday\"}"), 201), "$.id");
        mvc.perform(get("/v1/wishlists").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(wishlistId))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.offset").value(0));
        mvc.perform(json(patch("/v1/wishlists/" + wishlistId).with(alice()), "{\"name\":\"B-Day\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("B-Day"));
        mvc.perform(get("/v1/wishlists/" + wishlistId).with(bob()))
                .andExpect(status().isNotFound()); // not shared yet -> hidden
    }

    @Test
    @Order(22)
    void listPagingIsCappedAndReportsTotals() throws Exception {
        // an oversized limit is clamped, not rejected
        mvc.perform(get("/v1/wishlists").param("limit", "5000").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(100));
        mvc.perform(get("/v1/wishlists").param("limit", "0").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(1));
        // paging past the end keeps the total but returns nothing
        mvc.perform(get("/v1/wishlists").param("limit", "10").param("offset", "50").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.offset").value(50));
    }

    @Test
    @Order(21)
    void wishCrudWithLinks() throws Exception {
        String body = perform(json(post("/v1/wishes").with(alice()),
                "{\"wishlist_id\":\"" + wishlistId + "\",\"name\":\"Lego\",\"description\":\"big\","
                        + "\"quantity\":1,\"links\":[{\"url\":\"https://example.com\",\"label\":\"shop\"}]}"), 201);
        wishId = JsonPath.read(body, "$.id");
        assertThat((List<?>) JsonPath.read(body, "$.links")).hasSize(1);
        mvc.perform(get("/v1/wishes/" + wishId).with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claims").doesNotExist()); // owner view is claim-free by type
        // a wish is giftable once or unlimited — unit counts are not a thing
        mvc.perform(json(post("/v1/wishes").with(alice()),
                        "{\"wishlist_id\":\"" + wishlistId + "\",\"name\":\"Bricks\",\"quantity\":2,\"links\":[]}"))
                .andExpect(status().isBadRequest());
        unlimitedWishId = JsonPath.read(perform(json(post("/v1/wishes").with(alice()),
                "{\"wishlist_id\":\"" + wishlistId + "\",\"name\":\"Books\",\"quantity\":null,\"links\":[]}"), 201),
                "$.id");
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
                .andExpect(jsonPath("$.items[0].id").value(groupId))
                // the creator is admin; permission travels with the group, not the member list
                .andExpect(jsonPath("$.items[0].is_admin").value(true));
        mvc.perform(json(patch("/v1/groups/" + groupId).with(alice()), "{\"name\":\"Fam\",\"description\":null}"))
                .andExpect(status().isOk());

        String invite = perform(json(post("/v1/groups/" + groupId + "/invites").with(alice()),
                "{\"max_uses\":3}"), 201);
        inviteId = JsonPath.read(invite, "$.id");
        inviteCode = JsonPath.read(invite, "$.code");
        mvc.perform(get("/v1/groups/" + groupId + "/invites").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].use_count").value(0));
        // bob is no member yet: the group is hidden from him
        mvc.perform(get("/v1/groups/" + groupId + "/invites").with(bob()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("GROUP_NOT_FOUND"));
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
                .andExpect(jsonPath("$.total").value(4));
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
        // a revoked invite previews as its status alone
        mvc.perform(get("/v1/invites/" + inviteCode + "/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("revoked"))
                .andExpect(jsonPath("$.group_name").doesNotExist())
                .andExpect(jsonPath("$.member_count").doesNotExist());
        mvc.perform(get("/v1/groups/" + groupId + "/invites").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].revoked_at").isNotEmpty());
    }

    @Test
    @Order(33)
    void sharingWishlistIntoGroup() throws Exception {
        mvc.perform(json(post("/v1/groups/" + groupId + "/shares").with(alice()),
                        "{\"wishlist_id\":\"" + wishlistId + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/v1/groups/" + groupId + "/shares").with(bob()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].wishlist_id").value(wishlistId));
        // duplicate share rejected
        mvc.perform(json(post("/v1/groups/" + groupId + "/shares").with(alice()),
                        "{\"wishlist_id\":\"" + wishlistId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("WISHLIST_ALREADY_SHARED"));
        // bob (member) can now view the wishlist
        mvc.perform(get("/v1/wishlists/" + wishlistId).with(bob()))
                .andExpect(status().isOk());
        // shareable list is filtered in SQL: the just-shared list is gone for alice,
        // and bob (who owns nothing here) sees an empty list
        mvc.perform(get("/v1/groups/" + groupId + "/shareable-wishlists").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/v1/groups/" + groupId + "/shareable-wishlists").with(bob()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
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
        // a fresh claim is private: co-claiming is bob's call, not the wish owner's
        mvc.perform(post("/v1/claims/" + bobClaimId + "/participants").with(carol()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("CLAIM_NOT_SHARED"));
        // only bob may open his own claim
        mvc.perform(json(patch("/v1/claims/" + bobClaimId).with(carol()), "{\"is_shared\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("NOT_CLAIM_OWNER"));
        mvc.perform(json(patch("/v1/claims/" + bobClaimId).with(bob()), "{\"is_shared\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_shared").value(true));
        // carol pools money on bob's now-shared claim
        mvc.perform(post("/v1/claims/" + bobClaimId + "/participants").with(carol()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.participants.length()").value(2));
        // carol cannot also claim independently
        mvc.perform(json(post("/v1/claims").with(carol()), "{\"wish_id\":\"" + wishId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("ALREADY_CLAIMED"));
        // carol leaves the co-claim and tries her own -> the single unit is taken by bob
        mvc.perform(delete("/v1/claims/" + bobClaimId + "/participants/me").with(carol()))
                .andExpect(status().isNoContent());
        mvc.perform(json(post("/v1/claims").with(carol()), "{\"wish_id\":\"" + wishId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("WISH_FULLY_CLAIMED"));
        // the unlimited wish takes any number of independent claims
        daveClaimId = JsonPath.read(perform(json(post("/v1/claims").with(dave()),
                "{\"wish_id\":\"" + unlimitedWishId + "\"}"), 201), "$.claim_id");
        perform(json(post("/v1/claims").with(carol()), "{\"wish_id\":\"" + unlimitedWishId + "\"}"), 201);
    }

    @Test
    @Order(41)
    void ownerViewStaysClaimFreeSharedViewShowsClaims() throws Exception {
        String ownerView = perform(get("/v1/wishes").param("wishlist_id", wishlistId).with(alice()), 200);
        assertThat(ownerView).doesNotContain("claim", "participants", "remaining");
        String sharedView = perform(get("/v1/wishes").param("wishlist_id", wishlistId).with(bob()), 200);
        assertThat((Integer) JsonPath.read(sharedView, "$.items[0].remaining")).isZero();
        assertThat((List<?>) JsonPath.read(sharedView, "$.items[0].claims")).hasSize(1);
        // participation and claimability are answered by the backend, not derived by clients
        assertThat((Boolean) JsonPath.read(sharedView, "$.items[0].can_claim")).isFalse();
        List<Boolean> bobParticipation = JsonPath.read(sharedView, "$.items[0].claims[*].is_participant");
        assertThat(bobParticipation).containsExactly(true);
        // unlimited wish: no remaining count, and bob may still claim next to dave and carol
        assertThat((Integer) JsonPath.read(sharedView, "$.items[1].remaining")).isNull();
        assertThat((List<?>) JsonPath.read(sharedView, "$.items[1].claims")).hasSize(2);
        assertThat((Boolean) JsonPath.read(sharedView, "$.items[1].can_claim")).isTrue();
        // my-claims overview
        mvc.perform(get("/v1/claims").with(bob()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].wish_id").value(wishId));
    }

    @Test
    @Order(42)
    void quantityReductionGrandfathersClaims() throws Exception {
        // unlimited -> once while two claims exist: both stay valid, the owner is warned
        mvc.perform(json(patch("/v1/wishes/" + unlimitedWishId).with(alice()),
                        "{\"name\":\"Books\",\"quantity\":1,\"links\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existing_claims_affected").value(true));
        // counts stay rejected on update too
        mvc.perform(json(patch("/v1/wishes/" + unlimitedWishId).with(alice()),
                        "{\"name\":\"Books\",\"quantity\":3,\"links\":[]}"))
                .andExpect(status().isBadRequest());
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
    void lastAdminRulesAndLeaving() throws Exception {
        // dave leaves his claim first (claim disappears with last participant)
        mvc.perform(delete("/v1/claims/" + daveClaimId + "/participants/me").with(dave()))
                .andExpect(status().isNoContent());
        // carol (non-admin) leaves the group herself
        mvc.perform(delete("/v1/memberships/" + carolMembershipId).with(carol()))
                .andExpect(status().isNoContent());
        // her claims went with the membership: a claim must never outlive the access that
        // made its wishlist visible, otherwise the overview keeps serving wish and owner
        mvc.perform(get("/v1/claims").with(carol()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        String memberships = perform(get("/v1/memberships").param("group_id", groupId).with(alice()), 200);
        String aliceMembershipId = JsonPath.read(memberships,
                "$.items[?(@.is_owner == true)].id").toString().replaceAll("[\\[\\]\"]", "");
        // demote bob -> fine, alice is still admin
        mvc.perform(json(patch("/v1/memberships/" + bobMembershipId).with(alice()), "{\"is_admin\":false}"))
                .andExpect(status().isOk());
        // alice is now the LAST admin: cannot be demoted, cannot leave
        mvc.perform(json(patch("/v1/memberships/" + aliceMembershipId).with(alice()), "{\"is_admin\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("LAST_ADMIN_CANNOT_BE_DEMOTED"));
        mvc.perform(delete("/v1/memberships/" + aliceMembershipId).with(alice()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("LAST_ADMIN_CANNOT_LEAVE"));
        // promote bob again -> alice may leave (another admin remains)
        mvc.perform(json(patch("/v1/memberships/" + bobMembershipId).with(alice()), "{\"is_admin\":true}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/v1/memberships/" + aliceMembershipId).with(alice()))
                .andExpect(status().isNoContent());
        // the creator record moved to bob, so alice deleting her account cannot take the group with it
        String remaining = perform(get("/v1/memberships").param("group_id", groupId).with(bob()), 200);
        List<String> creators = JsonPath.read(remaining, "$.items[?(@.is_owner == true)].user.displayname");
        assertThat(creators).containsExactly("Bob Baker");
        // leaving also stopped the sharing: alice's list is gone from the group
        mvc.perform(get("/v1/groups/" + groupId + "/shares").with(bob()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @Order(51)
    void deletingGroupAndWishlist() throws Exception {
        // alice left the group, so for her it no longer exists (nor does its share)
        mvc.perform(delete("/v1/groups/" + groupId + "/shares/" + wishlistId).with(alice()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("GROUP_NOT_FOUND"));
        // carol is no member anymore -> the group answers like a missing one
        mvc.perform(delete("/v1/groups/" + groupId).with(carol()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("GROUP_NOT_FOUND"));
        // bob is group admin -> may delete the group
        mvc.perform(delete("/v1/groups/" + groupId).with(bob()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/v1/wishlists/" + wishlistId).with(alice()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/v1/wishlists").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        // the export still lists the soft-deleted wishlist, marked with deleted_at
        mvc.perform(get("/v1/users/me/export").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wishlists[0].deleted_at").isNotEmpty());
    }

    @Test
    @Order(52)
    void accountSoftDeleteAndRestoreOnRelogin() throws Exception {
        mvc.perform(delete("/v1/users/me").with(dave()))
                .andExpect(status().isNoContent());
        // logging in again within the purge window restores the account with its data
        RequestPostProcessor freshLogin = jwt()
                .jwt(jwt -> jwt.subject(DAVE.toString())
                        .claim("given_name", "Dave")
                        .claim("family_name", "Dent")
                        .claim("email", "dave@it.local")
                        .claim("auth_time", java.time.Instant.now().plusSeconds(5)))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_user")));
        mvc.perform(get("/v1/users/me").with(freshLogin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayname").value("Dave Dent"));
    }
}
