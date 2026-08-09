package de.marsg.wishlist.endpoints.dto.groups;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ShareWishlistDTO(
        @JsonProperty("wishlist_id") @NotNull UUID wishlistId) {
}
