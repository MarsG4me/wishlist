package de.marsg.wishlist.wishlist.request.dto.user;

import java.util.UUID;

public record SimpleUserReturnDTO (
    UUID id,
    String firstName,
    String lastName
){

}
