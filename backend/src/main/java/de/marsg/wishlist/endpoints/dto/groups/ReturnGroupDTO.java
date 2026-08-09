package de.marsg.wishlist.endpoints.dto.groups;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.jpa.entities.GroupEntity;
import java.util.UUID;

public record ReturnGroupDTO(
        @JsonProperty("id") UUID id,
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description) {

    public static ReturnGroupDTO from(GroupEntity group) {
        return new ReturnGroupDTO(group.getId(), group.getUsersId(), group.getName(),
                group.getDescription());
    }
}
