package de.marsg.wishlist.wishlist.request.dto.wishes;

import java.util.List;

public record ReturnClaimedWishesDTO(
    long total,
    int offset,
    int count,
    List<ReturnClaimedWishDTO> claims
) {

}
