package de.marsg.wishlist.endpoints.dto.claims;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateClaimDTO(
        @JsonProperty("wish_id") @NotNull UUID wishId) {
}
