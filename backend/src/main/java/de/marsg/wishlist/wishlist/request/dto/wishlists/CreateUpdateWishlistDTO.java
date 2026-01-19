package de.marsg.wishlist.wishlist.request.dto.wishlists;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUpdateWishlistDTO(
    
    @NotBlank(message = "Wishlist 'name' cannot be empty.")
    @Size(max = 20, message = "Wishlist name cannot exceed 20 characters.")
    @Size(min = 4, message = "Wishlist name must be longer than 4 characters.")
    String name

) {

}
