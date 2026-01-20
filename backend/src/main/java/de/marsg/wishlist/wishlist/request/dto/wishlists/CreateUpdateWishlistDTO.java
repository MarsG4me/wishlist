package de.marsg.wishlist.wishlist.request.dto.wishlists;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUpdateWishlistDTO(
    
    @NotBlank(message = "cannot be empty.")
    @Size(max = 20, message = "cannot exceed 20 characters.")
    @Size(min = 4, message = "must be longer than 4 characters.")
    String name

) {

}
