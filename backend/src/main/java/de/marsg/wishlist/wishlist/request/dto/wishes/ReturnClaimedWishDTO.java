package de.marsg.wishlist.wishlist.request.dto.wishes;

import java.util.UUID;

public record ReturnClaimedWishDTO(
    UUID id,
    String name,
    String description,
    boolean claimed,
    boolean bought
) {

}
