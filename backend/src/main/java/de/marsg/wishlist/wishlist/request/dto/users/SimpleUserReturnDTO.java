package de.marsg.wishlist.wishlist.request.dto.users;

import java.util.UUID;

public record SimpleUserReturnDTO (
    UUID id,
    String firstName,
    String lastName
){

}
