package de.marsg.wishlist.endpoints.dto.users;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePasswordDTO(
        @JsonProperty("current_password") @NotBlank String currentPassword,
        @JsonProperty("new_password") @NotBlank @Size(min = 8, max = 128) String newPassword) {
}
