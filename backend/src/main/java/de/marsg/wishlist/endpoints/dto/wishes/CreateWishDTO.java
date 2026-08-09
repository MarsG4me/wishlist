package de.marsg.wishlist.endpoints.dto.wishes;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateWishDTO(
        @JsonProperty("wishlist_id") @NotNull UUID wishlistId,
        @JsonProperty("name") @NotBlank @Size(max = 100) String name,
        @JsonProperty("description") @Size(max = 500) String description,
        /*
         * null = giftable unlimited times, 1 = giftable once. Arbitrary counts are not
         * offered: pooling for one expensive gift happens through open claims, not units.
         */
        @JsonProperty("quantity") @Min(1) @Max(1) Integer quantity,
        @JsonProperty("links") @Valid @Size(max = 20) List<WishLinkDTO> links) {
}
