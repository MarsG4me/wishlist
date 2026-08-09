package de.marsg.wishlist.endpoints.dto.memberships;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.endpoints.dto.users.ReturnSimpleUserDTO;
import de.marsg.wishlist.jpa.entities.GroupMembershipEntity;
import java.util.UUID;

public record ReturnMembershipDTO(
        @JsonProperty("id") UUID id,
        @JsonProperty("group_id") UUID groupId,
        @JsonProperty("user") ReturnSimpleUserDTO user,
        @JsonProperty("is_admin") boolean isAdmin,
        @JsonProperty("is_owner") boolean isOwner) {

    public static ReturnMembershipDTO from(GroupMembershipEntity membership, ReturnSimpleUserDTO user,
            boolean isOwner) {
        return new ReturnMembershipDTO(membership.getId(), membership.getGroupsId(), user,
                membership.isAdmin(), isOwner);
    }
}
