package de.marsg.wishlist.endpoints.dto.wishes;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.jpa.entities.WishEntity;
import java.util.List;
import java.util.UUID;

/**
 * Group-member view of a wish: includes claim state so members can coordinate gifting.
 * {@code remaining} is {@code null} for unlimited wishes and never below zero
 * (grandfathered claims can exceed a reduced quantity).
 *
 * @param canClaim whether the REQUESTER may still open a new claim here (free capacity
 *                 and no participation yet) — decided server-side, exactly like the
 *                 POST endpoint decides it.
 */
public record ReturnSharedWishDTO(
        @JsonProperty("id") UUID id,
        @JsonProperty("wishlist_id") UUID wishlistId,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("quantity") Integer quantity,
        @JsonProperty("remaining") Integer remaining,
        @JsonProperty("links") List<WishLinkDTO> links,
        @JsonProperty("claims") List<ReturnWishClaimDTO> claims,
        @JsonProperty("can_claim") boolean canClaim) {

    public static ReturnSharedWishDTO from(WishEntity wish, List<ReturnWishClaimDTO> claims) {
        Integer remaining = wish.getQuantity() == null
                ? null
                : Math.max(0, wish.getQuantity() - claims.size());
        boolean alreadyParticipating = claims.stream().anyMatch(ReturnWishClaimDTO::isParticipant);
        boolean canClaim = !alreadyParticipating && (remaining == null || remaining > 0);
        // joining does not consume a unit, but one participation per wish is the hard rule
        List<ReturnWishClaimDTO> resolved = claims.stream()
                .map(claim -> claim.withCanJoin(!alreadyParticipating && claim.isShared()))
                .toList();
        return new ReturnSharedWishDTO(wish.getId(), wish.getWishlistsId(), wish.getName(),
                wish.getDescription(), wish.getQuantity(), remaining,
                wish.getLinks().stream().map(WishLinkDTO::from).toList(), resolved, canClaim);
    }
}
