package de.marsg.wishlist.endpoints.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.jpa.entities.UserEntity;
import de.marsg.wishlist.jpa.entities.UserTier;
import java.util.UUID;

/** Admin search result: account data only, no wishlist/group content. */
public record ReturnAdminUserDTO(
        @JsonProperty("id") UUID id,
        @JsonProperty("email") String email,
        @JsonProperty("displayname") String displayname,
        @JsonProperty("tier") UserTier tier) {

    public static ReturnAdminUserDTO from(UserEntity user) {
        return new ReturnAdminUserDTO(user.getId(), user.getEmail(), user.getDisplayname(), user.getTier());
    }
}
