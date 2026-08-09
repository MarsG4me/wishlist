package de.marsg.wishlist.endpoints.dto.users;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.jpa.entities.UserTier;
import jakarta.validation.constraints.NotNull;

public record UpdateTierDTO(
        @JsonProperty("tier") @NotNull UserTier tier) {
}
