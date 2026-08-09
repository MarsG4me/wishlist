package de.marsg.wishlist.endpoints.dto.users;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.jpa.entities.UserEntity;
import java.util.UUID;

public record ReturnSimpleUserDTO(
        @JsonProperty("id") UUID id,
        @JsonProperty("displayname") String displayname) {

    public static ReturnSimpleUserDTO from(UserEntity user) {
        return new ReturnSimpleUserDTO(user.getId(), user.getDisplayname());
    }
}
