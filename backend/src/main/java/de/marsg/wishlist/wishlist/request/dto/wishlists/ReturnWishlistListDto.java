package de.marsg.wishlist.wishlist.request.dto.wishlists;

import java.util.List;

public record ReturnWishlistListDto(
        long total,
        int offset,
        long count,
        List<ReturnWishlistDto> wishlists) {

}
