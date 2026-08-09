package de.marsg.wishlist.endpoints.dto.claims;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.endpoints.dto.users.ReturnSimpleUserDTO;
import java.util.List;
import java.util.UUID;

/**
 * One of the requester's claim participations, with context for the overview page.
 *
 * @param isOwner  whether the requester created the claim and may therefore open or close it
 * @param isShared whether the claim currently accepts co-claimers
 */
public record ReturnMyClaimDTO(
        @JsonProperty("claim_id") UUID claimId,
        @JsonProperty("wish_id") UUID wishId,
        @JsonProperty("wish_name") String wishName,
        @JsonProperty("wishlist_id") UUID wishlistId,
        @JsonProperty("wishlist_name") String wishlistName,
        @JsonProperty("wishlist_owner") ReturnSimpleUserDTO wishlistOwner,
        @JsonProperty("participants") List<ReturnSimpleUserDTO> participants,
        @JsonProperty("is_owner") boolean isOwner,
        @JsonProperty("is_shared") boolean isShared) {
}
