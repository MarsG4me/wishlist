package de.marsg.wishlist.wishlist.request.dto.wishes;

import jakarta.validation.constraints.NotBlank;

public record UpdateWishClaimDTO(

    @NotBlank(message = "Field 'bought' cannot be empty.")
    boolean bought
) {

}
