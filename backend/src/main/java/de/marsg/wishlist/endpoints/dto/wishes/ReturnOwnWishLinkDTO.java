package de.marsg.wishlist.endpoints.dto.wishes;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.jpa.entities.LinkBlockSource;
import de.marsg.wishlist.jpa.entities.WishLinkEntity;
import java.util.UUID;

/** Link as shown to its owner, including whether and why it is hidden from everyone else. */
public record ReturnOwnWishLinkDTO(
        @JsonProperty("id") UUID id,
        @JsonProperty("url") String url,
        @JsonProperty("label") String label,
        @JsonProperty("blocked") boolean blocked,
        @JsonProperty("blocked_by") LinkBlockSource blockedBy) {

    public static ReturnOwnWishLinkDTO from(WishLinkEntity link) {
        return new ReturnOwnWishLinkDTO(link.getId(), link.getUrl(), link.getLabel(), link.isBlocked(),
                link.getBlockedBy());
    }
}
