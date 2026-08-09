package de.marsg.wishlist.endpoints.dto.memberships;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinGroupDTO(
        @JsonProperty("code") @NotBlank @Size(max = 32) String code) {
}
