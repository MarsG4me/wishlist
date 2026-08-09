package de.marsg.wishlist.endpoints.dto.claims;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/** The claim creator opening their claim for co-claimers, or closing it again. */
public record UpdateClaimSharingDTO(
        @JsonProperty("is_shared") @NotNull Boolean isShared) {
}
