package de.marsg.wishlist.endpoints.dto.users;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.jpa.entities.UserEntity;
import java.util.UUID;

/**
 * @param displayname {@code null} when the requester shares no group with this user and
 *                    may therefore not learn who they are — the id still identifies the
 *                    row so clients can spot themselves. Clients render their own
 *                    placeholder for the null case.
 */
public record ReturnSimpleUserDTO(
        @JsonProperty("id") UUID id,
        @JsonProperty("displayname") String displayname) {

    public static ReturnSimpleUserDTO from(UserEntity user) {
        return new ReturnSimpleUserDTO(user.getId(), user.getDisplayname());
    }

    /** Present but unnamed: a co-claimer from a group the requester is not in. */
    public static ReturnSimpleUserDTO unnamed(UUID id) {
        return new ReturnSimpleUserDTO(id, null);
    }
}
