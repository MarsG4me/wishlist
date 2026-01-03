package de.marsg.wishlist.wishlist.request;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.marsg.wishlist.wishlist.request.dto.groups.CreateUpdateGroupDTO;
import de.marsg.wishlist.wishlist.request.handler.JwtToUserDto;
import de.marsg.wishlist.wishlist.services.GroupService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/groups")
public class GroupController {


    private final GroupService groupService;
    private final JwtToUserDto jwtToDto;

    public GroupController(GroupService groupService, JwtToUserDto jwtToDto) {
        this.groupService = groupService;
        this.jwtToDto = jwtToDto;
    }

    @PostMapping
    public ResponseEntity<Object> createGroup(@Valid @RequestBody CreateUpdateGroupDTO dto, 
        @AuthenticationPrincipal Jwt jwt) {

        return groupService.create(dto, jwtToDto.convert(jwt));
    }

    @GetMapping
    public ResponseEntity<Object> getAllGroupsOfUser(
            @RequestParam(name = "offset", required = false, defaultValue = "0") int offset,
            @AuthenticationPrincipal Jwt jwt) {

        // Simple validation
        if (offset < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Offset must be >= 0", "errId", "INVALID_OFFSET"));
        }

        return groupService.getGroups(jwtToDto.convert(jwt), offset);
    }

    @GetMapping("/{group_id}")
    public ResponseEntity<Object> getGroup(@PathVariable("group_id") UUID groupId, 
    @AuthenticationPrincipal Jwt jwt) {

        return groupService.getGroup(groupId, jwtToDto.convert(jwt));
    }

    @PutMapping("/{group_id}")
    public ResponseEntity<Object> updateGroup(
            @Valid @RequestBody CreateUpdateGroupDTO dto,
            @PathVariable("group_id") UUID groupId, @AuthenticationPrincipal Jwt jwt) {

        return groupService.updateGroup(dto, groupId, jwtToDto.convert(jwt));
    }
    
    @DeleteMapping("/{group_id}")
    public ResponseEntity<Object> deleteGroup(@PathVariable("group_id") UUID groupId, 
    @AuthenticationPrincipal Jwt jwt) {

        return groupService.deleteGroup(groupId, jwtToDto.convert(jwt));
    }
}
