package de.marsg.wishlist.endpoints.dto.users;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.jpa.entities.UserEntity;
import de.marsg.wishlist.jpa.entities.UserTier;
import java.util.UUID;

public record ReturnOwnUserDTO(
        @JsonProperty("id") UUID id,
        @JsonProperty("displayname") String displayname,
        @JsonProperty("email") String email,
        @JsonProperty("tier") UserTier tier) {

    public static ReturnOwnUserDTO from(UserEntity user) {
        return new ReturnOwnUserDTO(user.getId(), user.getDisplayname(), user.getEmail(), user.getTier());
    }
}
