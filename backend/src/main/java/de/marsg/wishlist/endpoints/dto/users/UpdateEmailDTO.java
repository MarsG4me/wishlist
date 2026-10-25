package de.marsg.wishlist.endpoints.dto.users;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateEmailDTO(
        @JsonProperty("email") @NotBlank @Email @Size(max = 255) String email,
        @JsonProperty("current_password") @NotBlank String currentPassword) {
}
