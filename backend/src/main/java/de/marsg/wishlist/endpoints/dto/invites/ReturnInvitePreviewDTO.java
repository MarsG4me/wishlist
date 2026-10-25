package de.marsg.wishlist.endpoints.dto.invites;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Public invite teaser; a non-valid invite carries only {@code status}. Member-only fields
 * ({@code already_member}, {@code group_id}) never reach strangers.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReturnInvitePreviewDTO(
        @JsonProperty("group_name") String groupName,
        @JsonProperty("creator_displayname") String creatorDisplayname,
        @JsonProperty("member_count") Long memberCount,
        @JsonProperty("expires_at") Instant expiresAt,
        @JsonProperty("status") String status,
        @JsonProperty("already_member") @JsonInclude(JsonInclude.Include.NON_NULL) Boolean alreadyMember,
        @JsonProperty("group_id") @JsonInclude(JsonInclude.Include.NON_NULL) UUID groupId) {
}
