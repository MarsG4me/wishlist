package de.marsg.wishlist.endpoints.dto.wishes;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.jpa.entities.WishEntity;

/**
 * Update response: like the owner view plus a warning flag. When the new quantity is
 * below the number of existing claims, those claims are grandfathered and the owner is
 * told only THAT claims exist — never who.
 */
public record ReturnUpdatedWishDTO(
        @JsonProperty("wish") ReturnOwnWishDTO wish,
        @JsonProperty("existing_claims_affected") boolean existingClaimsAffected) {

    public static ReturnUpdatedWishDTO from(WishEntity wish, boolean existingClaimsAffected) {
        return new ReturnUpdatedWishDTO(ReturnOwnWishDTO.from(wish), existingClaimsAffected);
    }
}
