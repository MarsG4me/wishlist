package de.marsg.wishlist.endpoints.dto.wishlists;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.jpa.entities.WishlistEntity;
import java.util.UUID;

public record ReturnWishlistDTO(
        @JsonProperty("id") UUID id,
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("name") String name) {

    public static ReturnWishlistDTO from(WishlistEntity wishlist) {
        return new ReturnWishlistDTO(wishlist.getId(), wishlist.getUsersId(), wishlist.getName());
    }
}
