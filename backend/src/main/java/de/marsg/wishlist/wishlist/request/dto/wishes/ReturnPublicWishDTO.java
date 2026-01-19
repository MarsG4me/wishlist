package de.marsg.wishlist.wishlist.request.dto.wishes;

import java.util.UUID;

public record ReturnPublicWishDTO(
    UUID id,
    String name,
    String description,
    boolean unlimited,
    boolean claimed
) {

}
