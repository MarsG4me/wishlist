package de.marsg.wishlist.wishlist.request.dto.memberships;

import jakarta.validation.constraints.NotNull;

public record UpdateAdminStateDTO(

    @NotNull(message = "'setAdmin' field is required and must be true or false.")
    Boolean setAdmin
) {

}
