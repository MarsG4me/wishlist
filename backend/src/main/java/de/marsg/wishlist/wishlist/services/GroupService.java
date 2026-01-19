package de.marsg.wishlist.wishlist.services;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.marsg.wishlist.wishlist.data.jpa.entity.Group;
import de.marsg.wishlist.wishlist.data.jpa.entity.GroupMembership;
import de.marsg.wishlist.wishlist.data.jpa.entity.User;
import de.marsg.wishlist.wishlist.data.jpa.repository.GroupMembershipRepository;
import de.marsg.wishlist.wishlist.data.jpa.repository.GroupRepository;
import de.marsg.wishlist.wishlist.logging.LogMgr;
import de.marsg.wishlist.wishlist.request.dto.groups.CreateUpdateGroupDTO;
import de.marsg.wishlist.wishlist.request.dto.groups.ReturnGroupDTO;
import de.marsg.wishlist.wishlist.request.dto.groups.ReturnGroupListDTO;
import de.marsg.wishlist.wishlist.request.dto.groups.ReturnSimpleGroupDTO;
import de.marsg.wishlist.wishlist.request.dto.users.UserDTO;

@Service
public class GroupService {

    private static final int MAX_GROUPS_OWNED_PER_USER = 5;

    private UserService userService;
    private GroupRepository groupRepository;
    private GroupMembershipRepository membershipRepository;
    private LogMgr log;

    public GroupService(UserService userService, GroupRepository groupRepository,
            GroupMembershipRepository membershipRepository,
            LogMgr log) {
        this.userService = userService;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.log = log;
    }

    @Transactional
    public ResponseEntity<Object> create(CreateUpdateGroupDTO dto, UserDTO userDto) {

        if (!userService.ensuredUserExistance(userDto)) {
            // Return user verification error
            return returnFailedUserVerification();
        }

        if (!canCreateMoreGroups(userDto)) {

            log.logWarning("User %s tried to create a 6. group.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("error", "User can't create more groups.",
                            "errId", "USER_REACHED_GROUP_CREATION_LIMIT"));
        }

        // Handle group creation and membership assignment here

        User user = userService.getUser(userDto);

        Group group = new Group(user);
        group.setName(dto.name());
        group.setDescription(dto.description());

        group = groupRepository.save(group);

        GroupMembership membership = new GroupMembership(group, user);
        membership.setAdmin(true);

        membershipRepository.save(membership);

        log.logInfo("User %s created the new group '%s'.", userDto.getFirstName(), group.getName());

        ReturnGroupDTO returnDto = new ReturnGroupDTO(
                group.getId(),
                group.getName(),
                group.getDescription(),
                membershipRepository.countGroupMembers(group.getId()));

        URI location = ServletUriComponentsBuilder
        .fromCurrentRequest()      // captures /api/groups
        .path("/{id}")             // append the new resource ID
        .buildAndExpand(group.getId())
        .toUri();


        return ResponseEntity.created(location).body(returnDto);
    }

    public ResponseEntity<Object> getGroup(UUID groupId, UserDTO user) {

        if (!userService.ensuredUserExistance(user)) {
            // Return user verification error
            return returnFailedUserVerification();
        }

        Optional<Group> group = groupRepository.findById(groupId);
        if (group.isEmpty()) {

            log.logInfo("User %s tried to fetch a group that doesn't exist.", user.getFirstName());

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "Group not found.",
                            "errId", "GROUP_NOT_FOUND"));
        }

        if (!membershipRepository.existsByGroupIdAndUserId(groupId, user.getId())) {
            log.logInfo("User %s tried to access a group but is not a member.", user.getFirstName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("error", "User is not a member of that group.",
                            "errId", "NOT_A_GROUP_MEMBER"));
        }

        ReturnGroupDTO returnDto = new ReturnGroupDTO(
                group.get().getId(),
                group.get().getName(),
                group.get().getDescription(),
                membershipRepository.countGroupMembers(group.get().getId()));

        return ResponseEntity.ok().body(returnDto);
    }

    public ResponseEntity<Object> getGroups(UserDTO user, int offset) {

        if (!userService.ensuredUserExistance(user)) {
            // Return user verification error
            return returnFailedUserVerification();
        }

        log.logInfo("Groups for %s are being fetched.", user.getFirstName());

        long total = membershipRepository.countUsersGroups(user.getId());
        List<ReturnSimpleGroupDTO> groupDtos = membershipRepository.findAllGroupsByUserId(user.getId(), offset)
                .stream().map(g -> new ReturnSimpleGroupDTO(
                        g.getId(),
                        g.getName()))
                .toList();

        log.logInfo("Returning DTO for %s containing %d groups..", user.getFirstName(), total);

        ReturnGroupListDTO returnDto = new ReturnGroupListDTO(total, offset, groupDtos.size(), groupDtos);

        return ResponseEntity.ok().body(returnDto);
    }

    public ResponseEntity<Object> updateGroup(CreateUpdateGroupDTO dto, UUID groupId, UserDTO user) {

        if (!userService.ensuredUserExistance(user)) {
            // Return user verification error
            return returnFailedUserVerification();
        }

        Optional<Group> group = groupRepository.findById(groupId);
        if (group.isEmpty()) {
            log.logInfo("User %s tried to update a group that doesn't exist.", user.getFirstName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "Group not found.",
                            "errId", "GROUP_NOT_FOUND"));
        }

        Optional<GroupMembership> membership = membershipRepository.findMembershipByGroupIdAndUserId(groupId,
                user.getId());

        if (membership.isEmpty()) {
            log.logInfo("User %s tried to update a group but is not a member.", user.getFirstName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("error", "User is not a member of that group.",
                            "errId", "NOT_A_GROUP_MEMBER"));
        }

        if (!membership.get().isAdmin()) {
            log.logInfo("User %s tried to update a group but is not an admin.", user.getFirstName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("error", "User is not an admin of that group.",
                            "errId", "NOT_A_GROUP_ADMIN"));
        }

        group.get().setName(dto.name());
        group.get().setDescription(dto.description());

        groupRepository.save(group.get());

        log.logInfo("User %s updated a group.", user.getFirstName());

        ReturnGroupDTO returnDto = new ReturnGroupDTO(
                group.get().getId(),
                group.get().getName(),
                group.get().getDescription(),
                membershipRepository.countGroupMembers(group.get().getId()));

        return ResponseEntity.ok().body(returnDto);
    }

    @Transactional
    public ResponseEntity<Object> deleteGroup(UUID groupId, UserDTO userDto) {

        if (!userService.ensuredUserExistance(userDto)) {
            // Return user verification error
            return returnFailedUserVerification();
        }

        Optional<Group> group = groupRepository.findById(groupId);
        if (group.isEmpty()) {
            log.logInfo("User %s tried to delete a group that doesn't exist.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "Group not found.",
                            "errId", "GROUP_NOT_FOUND"));
        }

        if (!group.get().getOwner().getKeycloakId().equals(userDto.getId())) {
            log.logInfo("User %s tried to delete a group of which they are not the creator.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("error", "Can only be done by group owner.",
                            "errId", "NOT_THE_GROUP_OWNER"));
        }

        membershipRepository.deleteByGroupId(groupId);

        groupRepository.delete(group.get());

        log.logInfo("User %s deleted the group %s.", userDto.getFirstName(), group.get().getName());

        return ResponseEntity.ok().body(Map.of());
    }

    /*
     * Helper
     */

    private boolean canCreateMoreGroups(UserDTO user) {
        return groupRepository.findAllByOwner(userService.getUser(user)).size() < MAX_GROUPS_OWNED_PER_USER;
    }

    private ResponseEntity<Object> returnFailedUserVerification() {
        return ResponseEntity.internalServerError().body(
                Map.of("error", "User verification failed.",
                        "errId", "USER_VERIFICATION_FAILED"));
    }
}
