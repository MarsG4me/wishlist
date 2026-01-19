package de.marsg.wishlist.wishlist.request.dto.users;

import java.util.UUID;

public record ReturnOwnUserDTO(
    UUID id,
    String firstName,
    String lastName,
    String email
) {

}
