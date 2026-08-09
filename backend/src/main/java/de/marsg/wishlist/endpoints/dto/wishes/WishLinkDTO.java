package de.marsg.wishlist.endpoints.dto.wishes;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.jpa.entities.WishLinkEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Input link; position derives from list order. */
public record WishLinkDTO(
        @JsonProperty("url") @NotBlank @Size(max = 2048) String url,
        @JsonProperty("label") @Size(max = 100) String label) {

    public static WishLinkDTO from(WishLinkEntity link) {
        return new WishLinkDTO(link.getUrl(), link.getLabel());
    }
}
