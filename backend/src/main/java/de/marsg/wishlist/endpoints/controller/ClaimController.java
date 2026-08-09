package de.marsg.wishlist.endpoints.controller;

import de.marsg.wishlist.configs.security.AuthenticatedUser;
import de.marsg.wishlist.endpoints.dto.claims.CreateClaimDTO;
import de.marsg.wishlist.endpoints.dto.claims.ReturnMyClaimDTO;
import de.marsg.wishlist.endpoints.dto.common.ReturnListDTO;
import de.marsg.wishlist.endpoints.dto.wishes.ReturnWishClaimDTO;
import de.marsg.wishlist.endpoints.services.ClaimService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/claims")
@Tag(name = "claims")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @GetMapping
    @Operation(summary = "Own claim participations with wish/wishlist context "
            + "(limit is clamped to 1..100)")
    public ReturnListDTO<ReturnMyClaimDTO> listMine(AuthenticatedUser user,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", required = false) Integer offset) {
        return claimService.listMine(user.userId(), limit, offset);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Claim a wish (group member, not the owner, capacity-checked under lock)")
    public ReturnWishClaimDTO create(AuthenticatedUser user, @Valid @RequestBody CreateClaimDTO body) {
        return claimService.create(user.userId(), body.wishId());
    }

    @PostMapping("/{id}/participants")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Join an existing claim to pool money (co-claiming)")
    public ReturnWishClaimDTO join(AuthenticatedUser user, @PathVariable("id") UUID id) {
        return claimService.join(user.userId(), id);
    }

    @DeleteMapping("/{id}/participants/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Leave a claim; the claim disappears when the last participant leaves")
    public void leave(AuthenticatedUser user, @PathVariable("id") UUID id) {
        claimService.leave(user.userId(), id);
    }
}
