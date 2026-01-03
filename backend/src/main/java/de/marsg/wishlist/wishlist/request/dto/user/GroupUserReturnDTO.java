

package de.marsg.wishlist.wishlist.request.dto.user;

import java.util.UUID;

public record GroupUserReturnDTO (
    UUID id,
    String firstName,
    String lastName,
    Boolean isAdmin
){

}
