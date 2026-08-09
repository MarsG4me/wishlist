package de.marsg.wishlist.endpoints.dto.wishlists;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUpdateWishlistDTO(
        @JsonProperty("name") @NotBlank @Size(max = 100) String name) {
}
