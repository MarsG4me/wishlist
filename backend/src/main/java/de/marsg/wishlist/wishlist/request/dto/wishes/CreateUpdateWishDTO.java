package de.marsg.wishlist.wishlist.request.dto.wishes;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUpdateWishDTO(
    
    @NotBlank(message = "cannot be empty.")
    @NotNull(message = "is required and must be true or false.")
    @Size(max = 20, message = "cannot exceed 20 characters.")
    @Size(min = 4, message = "must be longer than 4 characters.")
    String name,

    @NotNull(message = "is required and must be true or false.")
    boolean unlimited,

    @NotNull(message = "cannot be null.")
    UUID wishlistId,

    @Size(max = 60, message = "cannot exceed 60 characters.")
    String description

) {
    public String description(){
        return description == null ? "" : description;
    }
}
