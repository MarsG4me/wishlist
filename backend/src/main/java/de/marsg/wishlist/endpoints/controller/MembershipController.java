package de.marsg.wishlist.endpoints.controller;

import de.marsg.wishlist.configs.security.AuthenticatedUser;
import de.marsg.wishlist.endpoints.dto.common.ReturnListDTO;
import de.marsg.wishlist.endpoints.dto.memberships.JoinGroupDTO;
import de.marsg.wishlist.endpoints.dto.memberships.ReturnMembershipDTO;
import de.marsg.wishlist.endpoints.dto.memberships.UpdateAdminStateDTO;
import de.marsg.wishlist.endpoints.services.MembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/memberships")
@Tag(name = "memberships")
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @PostMapping("/join")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Join a group by invite code")
    public ReturnMembershipDTO join(AuthenticatedUser user, @Valid @RequestBody JoinGroupDTO body) {
        return membershipService.join(user.userId(), body.code());
    }

    @GetMapping
    @Operation(summary = "Members of a group (member; limit is clamped to 1..100)")
    public ReturnListDTO<ReturnMembershipDTO> list(AuthenticatedUser user,
            @RequestParam("group_id") UUID groupId,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", required = false) Integer offset) {
        return membershipService.list(user.userId(), groupId, limit, offset);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Grant/revoke the admin flag (group admin); the last admin cannot be demoted")
    public ReturnMembershipDTO setAdmin(AuthenticatedUser user, @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateAdminStateDTO body) {
        return membershipService.setAdmin(user.userId(), id, body.isAdmin());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a member (group admin) or leave yourself; the last admin cannot leave")
    public void remove(AuthenticatedUser user, @PathVariable("id") UUID id) {
        membershipService.remove(user.userId(), id);
    }
}
