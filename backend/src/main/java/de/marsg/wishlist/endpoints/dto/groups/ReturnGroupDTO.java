package de.marsg.wishlist.endpoints.dto.groups;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.jpa.entities.GroupEntity;
import java.util.UUID;

/**
 * @param isAdmin the REQUESTER's own permission in this group, resolved by a boolean
 *                query. Clients must use this instead of searching the member list,
 *                which is paged and may not contain the requester's row at all.
 */
public record ReturnGroupDTO(
        @JsonProperty("id") UUID id,
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("is_admin") boolean isAdmin) {

    public static ReturnGroupDTO from(GroupEntity group, boolean isAdmin) {
        return new ReturnGroupDTO(group.getId(), group.getUsersId(), group.getName(),
                group.getDescription(), isAdmin);
    }
}
