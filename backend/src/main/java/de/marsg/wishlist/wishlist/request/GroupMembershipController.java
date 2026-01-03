package de.marsg.wishlist.wishlist.request;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.marsg.wishlist.wishlist.request.dto.memberships.UpdateAdminStateDTO;
import de.marsg.wishlist.wishlist.request.handler.JwtToUserDto;
import de.marsg.wishlist.wishlist.services.GroupMembershipService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/memberships")
public class GroupMembershipController {

    private final JwtToUserDto jwtToDto;
    private final GroupMembershipService membershipService;

    public GroupMembershipController(JwtToUserDto jwtToDto, GroupMembershipService membershipService) {
        this.membershipService = membershipService;
        this.jwtToDto = jwtToDto;
    }

    @GetMapping("/{group_id}")
    public ResponseEntity<Object> getGroupMembers(
            @RequestParam(name = "offset", required = false, defaultValue = "0") int offset,
            @PathVariable("group_id") UUID groupId,
            @AuthenticationPrincipal Jwt jwt) {

                
        // Simple validation
        if (offset < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Offset must be >= 0", "errId", "INVALID_OFFSET"));
        }
   
        return membershipService.getGroupsMembers(groupId, jwtToDto.convert(jwt), offset);
    }

    @PutMapping("/{group_id}/{user_id}")
    public ResponseEntity<Object> manageUserAdminRank(
            @Valid @RequestBody UpdateAdminStateDTO dto,
            @PathVariable("group_id") UUID groupId,
            @PathVariable("user_id") UUID userId,
            @AuthenticationPrincipal Jwt jwt) {
   
        return membershipService.updateUsersAdminState(groupId, userId, jwtToDto.convert(jwt), dto.setAdmin());
    }

    @DeleteMapping("/{group_id}/{user_id}")
    public ResponseEntity<Object> removeUserFromGroup(
            @PathVariable("group_id") UUID groupId,
            @PathVariable("user_id") UUID userId,
            @AuthenticationPrincipal Jwt jwt) {
   
        return membershipService.removeUserFromGroup(groupId, userId, jwtToDto.convert(jwt));
    }
}
