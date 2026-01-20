package de.marsg.wishlist.wishlist.request.dto.wishes;


import jakarta.validation.constraints.NotNull;

public record UpdateWishClaimDTO(

    @NotNull(message = "cannot be null.")
    boolean bought
) {
}
