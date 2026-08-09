package de.marsg.wishlist.endpoints.dto.groups;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUpdateGroupDTO(
        @JsonProperty("name") @NotBlank @Size(max = 100) String name,
        @JsonProperty("description") @Size(max = 500) String description) {
}
