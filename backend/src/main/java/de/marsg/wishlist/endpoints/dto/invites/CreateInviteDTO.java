package de.marsg.wishlist.endpoints.dto.invites;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;

public record CreateInviteDTO(
        /* null = unlimited uses within the 24h validity window */
        @JsonProperty("max_uses") @Min(1) Integer maxUses) {
}
