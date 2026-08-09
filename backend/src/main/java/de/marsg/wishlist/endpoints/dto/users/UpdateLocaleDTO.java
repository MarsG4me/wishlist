package de.marsg.wishlist.endpoints.dto.users;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.jpa.entities.UserLocale;
import jakarta.validation.constraints.NotNull;

public record UpdateLocaleDTO(
        @JsonProperty("locale") @NotNull UserLocale locale) {
}
