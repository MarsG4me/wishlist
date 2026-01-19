package de.marsg.wishlist.wishlist.request.dto.wishlists;

import java.util.UUID;

public record ReturnWishlistDto(
        UUID id,
        String name,
        long wishes,
        UUID ownerId) {

}
