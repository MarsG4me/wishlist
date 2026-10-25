package de.marsg.wishlist.endpoints.dto.wishes;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.jpa.entities.WishLinkEntity;
import java.util.UUID;

/** Link as shown to viewers; blocked links are filtered out before this type is built. */
public record ReturnWishLinkDTO(
        @JsonProperty("id") UUID id,
        @JsonProperty("url") String url,
        @JsonProperty("label") String label) {

    public static ReturnWishLinkDTO from(WishLinkEntity link) {
        return new ReturnWishLinkDTO(link.getId(), link.getUrl(), link.getLabel());
    }
}
