package de.marsg.wishlist.endpoints.dto.memberships;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record UpdateAdminStateDTO(
        @JsonProperty("is_admin") @NotNull Boolean isAdmin) {
}
