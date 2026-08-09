package de.marsg.wishlist.endpoints.dto.wishes;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.endpoints.dto.users.ReturnSimpleUserDTO;
import java.util.List;
import java.util.UUID;

/**
 * A claim as seen by group members: who is gifting together.
 *
 * @param isParticipant whether the REQUESTER is part of this claim — resolved server-side
 *                      so clients never have to search the participant list.
 * @param isOwner       whether the REQUESTER created this claim, i.e. may open or close it.
 * @param isShared      whether the creator opened the claim up for co-claimers.
 * @param canJoin       whether the REQUESTER may join right now — decided server-side,
 *                      exactly like the participants endpoint decides it.
 */
public record ReturnWishClaimDTO(
        @JsonProperty("claim_id") UUID claimId,
        @JsonProperty("participants") List<ReturnSimpleUserDTO> participants,
        @JsonProperty("is_participant") boolean isParticipant,
        @JsonProperty("is_owner") boolean isOwner,
        @JsonProperty("is_shared") boolean isShared,
        @JsonProperty("can_join") boolean canJoin) {

    /**
     * Joinability also depends on the requester's participation in SIBLING claims of the
     * same wish, which only the enclosing wish knows — hence it is filled in there.
     */
    public ReturnWishClaimDTO withCanJoin(boolean canJoin) {
        return new ReturnWishClaimDTO(claimId, participants, isParticipant, isOwner, isShared, canJoin);
    }
}
