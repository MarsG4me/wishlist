package de.marsg.wishlist.endpoints.dto.users;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.jpa.entities.UserLocale;
import de.marsg.wishlist.jpa.entities.UserTier;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Single-document personal data export (GDPR Art. 15/20); not a paged list. */
public record ReturnUserExportDTO(
        @JsonProperty("exported_at") Instant exportedAt,
        @JsonProperty("user") ExportUser user,
        @JsonProperty("keycloak") ExportKeycloak keycloak,
        @JsonProperty("wishlists") List<ExportWishlist> wishlists,
        @JsonProperty("memberships") List<ExportMembership> memberships,
        @JsonProperty("groups_created") List<ExportGroup> groupsCreated,
        @JsonProperty("invites_created") List<ExportInvite> invitesCreated,
        @JsonProperty("shares") List<ExportShare> shares,
        @JsonProperty("claims") List<ExportClaim> claims) {

    public record ExportUser(
            @JsonProperty("id") UUID id,
            @JsonProperty("displayname") String displayname,
            @JsonProperty("email") String email,
            @JsonProperty("locale") UserLocale locale,
            @JsonProperty("tier") UserTier tier,
            @JsonProperty("created_at") Instant createdAt) {
    }

    /** Account data held by the identity provider; null in the export when Keycloak was unreachable. */
    public record ExportKeycloak(
            @JsonProperty("username") String username,
            @JsonProperty("email") String email,
            @JsonProperty("email_verified") Boolean emailVerified,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            @JsonProperty("created_timestamp") Long createdTimestamp,
            @JsonProperty("federated_identities") List<ExportFederatedIdentity> federatedIdentities) {
    }

    public record ExportFederatedIdentity(
            @JsonProperty("identity_provider") String identityProvider,
            @JsonProperty("user_name") String userName) {
    }

    public record ExportWishlist(
            @JsonProperty("id") UUID id,
            @JsonProperty("name") String name,
            @JsonProperty("deleted_at") Instant deletedAt,
            @JsonProperty("wishes") List<ExportWish> wishes) {
    }

    public record ExportWish(
            @JsonProperty("id") UUID id,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("quantity") Integer quantity,
            @JsonProperty("deleted_at") Instant deletedAt,
            @JsonProperty("links") List<ExportLink> links) {
    }

    public record ExportLink(
            @JsonProperty("url") String url,
            @JsonProperty("label") String label,
            @JsonProperty("position") int position) {
    }

    public record ExportMembership(
            @JsonProperty("group_id") UUID groupId,
            @JsonProperty("group_name") String groupName,
            @JsonProperty("is_admin") boolean isAdmin,
            @JsonProperty("is_owner") boolean isOwner,
            @JsonProperty("joined_at") Instant joinedAt) {
    }

    public record ExportGroup(
            @JsonProperty("id") UUID id,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("deleted_at") Instant deletedAt) {
    }

    public record ExportInvite(
            @JsonProperty("code") String code,
            @JsonProperty("expires_at") Instant expiresAt,
            @JsonProperty("revoked_at") Instant revokedAt,
            @JsonProperty("max_uses") Integer maxUses,
            @JsonProperty("use_count") int useCount) {
    }

    public record ExportShare(
            @JsonProperty("wishlist_id") UUID wishlistId,
            @JsonProperty("group_id") UUID groupId) {
    }

    public record ExportClaim(
            @JsonProperty("claim_id") UUID claimId,
            @JsonProperty("wish_name") String wishName,
            @JsonProperty("wish_owner_displayname") String wishOwnerDisplayname,
            @JsonProperty("is_shared") boolean isShared,
            @JsonProperty("is_creator") boolean isCreator) {
    }
}
