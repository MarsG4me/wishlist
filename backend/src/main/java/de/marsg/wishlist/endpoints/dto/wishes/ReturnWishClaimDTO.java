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
 */
public record ReturnWishClaimDTO(
        @JsonProperty("claim_id") UUID claimId,
        @JsonProperty("participants") List<ReturnSimpleUserDTO> participants,
        @JsonProperty("is_participant") boolean isParticipant) {
}
