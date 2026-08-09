package de.marsg.wishlist.endpoints.dto.wishes;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Full replacement of the editable fields; links are replaced as a set. */
public record UpdateWishDTO(
        @JsonProperty("name") @NotBlank @Size(max = 100) String name,
        @JsonProperty("description") @Size(max = 500) String description,
        /* null = unlimited, 1 = once; see CreateWishDTO for why there is no count */
        @JsonProperty("quantity") @Min(1) @Max(1) Integer quantity,
        @JsonProperty("links") @Valid @Size(max = 20) List<WishLinkDTO> links) {
}
