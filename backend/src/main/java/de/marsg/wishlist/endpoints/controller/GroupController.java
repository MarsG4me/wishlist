package de.marsg.wishlist.endpoints.controller;

import de.marsg.wishlist.configs.security.AuthenticatedUser;
import de.marsg.wishlist.endpoints.dto.groups.CreateUpdateGroupDTO;
import de.marsg.wishlist.endpoints.dto.groups.ReturnGroupDTO;
import de.marsg.wishlist.endpoints.dto.groups.ReturnSharedWishlistDTO;
import de.marsg.wishlist.endpoints.dto.groups.ShareWishlistDTO;
import de.marsg.wishlist.endpoints.dto.invites.CreateInviteDTO;
import de.marsg.wishlist.endpoints.dto.invites.ReturnInviteDTO;
import de.marsg.wishlist.endpoints.services.GroupService;
import de.marsg.wishlist.endpoints.services.InviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/groups")
@Tag(name = "groups")
public class GroupController {

    private final GroupService groupService;
    private final InviteService inviteService;

    public GroupController(GroupService groupService, InviteService inviteService) {
        this.groupService = groupService;
        this.inviteService = inviteService;
    }

    @GetMapping
    @Operation(summary = "Groups the requester is a member of")
    public List<ReturnGroupDTO> list(AuthenticatedUser user) {
        return groupService.listOwn(user.userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a group; the owner gets an admin membership (limit-checked)")
    public ReturnGroupDTO create(AuthenticatedUser user, @Valid @RequestBody CreateUpdateGroupDTO body) {
        return groupService.create(user.userId(), user.tier(), body);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One group (member)")
    public ReturnGroupDTO get(AuthenticatedUser user, @PathVariable("id") UUID id) {
        return groupService.get(user.userId(), id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update name/description (owner or admin)")
    public ReturnGroupDTO update(AuthenticatedUser user, @PathVariable("id") UUID id,
            @Valid @RequestBody CreateUpdateGroupDTO body) {
        return groupService.update(user.userId(), id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a group (owner only)")
    public void delete(AuthenticatedUser user, @PathVariable("id") UUID id) {
        groupService.softDelete(user.userId(), id);
    }

    @GetMapping("/{id}/shares")
    @Operation(summary = "Wishlists shared into this group (member)")
    public List<ReturnSharedWishlistDTO> listShares(AuthenticatedUser user, @PathVariable("id") UUID id) {
        return groupService.listShares(user.userId(), id);
    }

    @PostMapping("/{id}/shares")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Share an own wishlist into the group (wishlist owner who is a member)")
    public void share(AuthenticatedUser user, @PathVariable("id") UUID id,
            @Valid @RequestBody ShareWishlistDTO body) {
        groupService.share(user.userId(), id, body.wishlistId());
    }

    @DeleteMapping("/{id}/shares/{wishlistId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a share (wishlist owner or group admin)")
    public void unshare(AuthenticatedUser user, @PathVariable("id") UUID id,
            @PathVariable("wishlistId") UUID wishlistId) {
        groupService.unshare(user.userId(), id, wishlistId);
    }

    @GetMapping("/{id}/invites")
    @Operation(summary = "Invite history incl. revoked/expired (owner or admin)")
    public List<ReturnInviteDTO> listInvites(AuthenticatedUser user, @PathVariable("id") UUID id) {
        return inviteService.list(user.userId(), id);
    }

    @PostMapping("/{id}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an invite code: valid 24h, optional max_uses (owner or admin)")
    public ReturnInviteDTO createInvite(AuthenticatedUser user, @PathVariable("id") UUID id,
            @Valid @RequestBody CreateInviteDTO body) {
        return inviteService.create(user.userId(), id, body.maxUses());
    }

    @DeleteMapping("/{id}/invites/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke an invite; the row is kept as join history")
    public void revokeInvite(AuthenticatedUser user, @PathVariable("id") UUID id,
            @PathVariable("inviteId") UUID inviteId) {
        inviteService.revoke(user.userId(), id, inviteId);
    }
}
