

package de.marsg.wishlist.wishlist.request.dto.users;

import java.util.UUID;

public record GroupUserReturnDTO (
    UUID id,
    String firstName,
    String lastName,
    Boolean isAdmin
){

}
