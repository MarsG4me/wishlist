package de.marsg.wishlist.wishlist.request.dto.memberships;

import jakarta.validation.constraints.NotNull;

public record UpdateAdminStateDTO(

    @NotNull(message = "is required and must be true or false.")
    boolean setAdmin
) {
}
