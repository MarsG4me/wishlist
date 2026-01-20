package de.marsg.wishlist.wishlist.request.dto.groups;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUpdateGroupDTO(

    @NotBlank(message = "cannot be empty.")
    @Size(max = 20, message = "cannot exceed 20 characters.")
    @Size(min = 4, message = "must be longer than 4 characters.")
    String name,

    @Size(max = 60, message = "cannot exceed 60 characters.")
    String description

) {

    public String description(){
        return description == null ? "" : description;
    }

}
