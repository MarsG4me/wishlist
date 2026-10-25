package de.marsg.wishlist.endpoints.dto.wishes;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.jpa.entities.WishEntity;
import java.util.List;
import java.util.UUID;

/**
 * Owner view of a wish. Deliberately has NO claim fields — the owner must never
 * learn who claimed; leaking is structurally impossible with this type.
 */
public record ReturnOwnWishDTO(
        @JsonProperty("id") UUID id,
        @JsonProperty("wishlist_id") UUID wishlistId,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("quantity") Integer quantity,
        @JsonProperty("links") List<ReturnOwnWishLinkDTO> links) {

    public static ReturnOwnWishDTO from(WishEntity wish) {
        return new ReturnOwnWishDTO(wish.getId(), wish.getWishlistsId(), wish.getName(),
                wish.getDescription(), wish.getQuantity(),
                wish.getLinks().stream().map(ReturnOwnWishLinkDTO::from).toList());
    }
}
