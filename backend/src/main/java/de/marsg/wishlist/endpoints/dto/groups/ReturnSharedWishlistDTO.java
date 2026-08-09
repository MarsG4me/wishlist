package de.marsg.wishlist.endpoints.dto.groups;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.endpoints.dto.users.ReturnSimpleUserDTO;
import java.util.UUID;

/** A wishlist shared into a group, with its owner for display. */
public record ReturnSharedWishlistDTO(
        @JsonProperty("wishlist_id") UUID wishlistId,
        @JsonProperty("name") String name,
        @JsonProperty("owner") ReturnSimpleUserDTO owner) {
}
