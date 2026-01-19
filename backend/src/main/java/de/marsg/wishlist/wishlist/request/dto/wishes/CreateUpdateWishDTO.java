package de.marsg.wishlist.wishlist.request.dto.wishes;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUpdateWishDTO(
    
    @NotBlank(message = "Wish 'name' cannot be empty.")
    @Size(max = 20, message = "Wish name cannot exceed 20 characters.")
    @Size(min = 4, message = "Wish name must be longer than 4 characters.")
    String name,

    @Size(max = 60, message = "Wish description cannot exceed 60 characters.")
    String description,
    
    @NotNull(message = "Wish 'unlimited' cannot be null.")
    boolean unlimited,

    @NotBlank(message = "Wish 'wishlist_id' cannot be empty.")
    @NotNull(message = "Wish 'wishlist_id' cannot be null.")
    UUID wishlist_id

) {

    public String description(){
        return description == null ? "" : description;
    }

}
