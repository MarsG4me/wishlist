package de.marsg.wishlist.endpoints.dto.invites;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.jpa.entities.GroupInviteEntity;
import java.time.Instant;
import java.util.UUID;

public record ReturnInviteDTO(
        @JsonProperty("id") UUID id,
        @JsonProperty("group_id") UUID groupId,
        @JsonProperty("code") String code,
        @JsonProperty("expires_at") Instant expiresAt,
        @JsonProperty("revoked_at") Instant revokedAt,
        @JsonProperty("max_uses") Integer maxUses,
        @JsonProperty("use_count") int useCount) {

    public static ReturnInviteDTO from(GroupInviteEntity invite) {
        return new ReturnInviteDTO(invite.getId(), invite.getGroupsId(), invite.getCode(),
                invite.getExpiresAt(), invite.getRevokedAt(), invite.getMaxUses(), invite.getUseCount());
    }
}
