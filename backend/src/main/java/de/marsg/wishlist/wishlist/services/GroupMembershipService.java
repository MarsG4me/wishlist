package de.marsg.wishlist.wishlist.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import de.marsg.wishlist.wishlist.data.jpa.entity.Group;
import de.marsg.wishlist.wishlist.data.jpa.entity.GroupMembership;
import de.marsg.wishlist.wishlist.data.jpa.entity.User;
import de.marsg.wishlist.wishlist.data.jpa.repository.GroupMembershipRepository;
import de.marsg.wishlist.wishlist.data.jpa.repository.GroupRepository;
import de.marsg.wishlist.wishlist.logging.LogMgr;
import de.marsg.wishlist.wishlist.request.dto.memberships.ReturnGroupMembersDTO;
import de.marsg.wishlist.wishlist.request.dto.user.GroupUserReturnDTO;
import de.marsg.wishlist.wishlist.request.dto.user.UserDTO;

@Service
public class GroupMembershipService {

    private final GroupMembershipRepository membershipRepository;
    private final LogMgr log;
    private final UserService userService;
    private final GroupRepository groupRepository;

    public GroupMembershipService(GroupMembershipRepository membershipRepository, LogMgr log, UserService userService,
            GroupRepository groupRepository) {
        this.membershipRepository = membershipRepository;
        this.log = log;
        this.userService = userService;
        this.groupRepository = groupRepository;
    }

    public ResponseEntity<Object> getGroupsMembers(UUID groupId, UserDTO userDto, int offset) {

        if (!userService.ensuredUserExistance(userDto)) {
            // Return user verification error
            return returnFailedUserVerification();
        }

        Optional<Group> group = groupRepository.findById(groupId);
        if (group.isEmpty()) {
            log.logInfo("User %s tried to update a group that doesn't exist.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "Group not found.",
                            "errId", "GROUP_NOT_FOUND"));
        }

        Optional<GroupMembership> membership = membershipRepository.findMembershipByGroupIdAndUserId(groupId,
                userDto.getId());

        if (membership.isEmpty()) {
            log.logInfo("User %s tried to update a group but is not a member.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("error", "User is not a member of that group.",
                            "errId", "NOT_A_GROUP_MEMBER"));
        }

        long memberCount = membershipRepository.countGroupMembers(groupId);

        List<User> members = membershipRepository.findAllUsersByGroupId(groupId, offset);

        List<GroupUserReturnDTO> memberDtos = members.stream()
                .map(user -> new GroupUserReturnDTO(user.getKeycloakId(), user.getFirstName(), user.getLastName(),
                        membershipRepository.findMembershipByGroupIdAndUserId(group.get().getId(), user.getKeycloakId())
                                .get().isAdmin()))
                .toList();

        return ResponseEntity.ok(new ReturnGroupMembersDTO(memberCount, offset, memberDtos.size(), memberDtos));

    }

    public ResponseEntity<Object> updateUsersAdminState(UUID groupId, UUID targetUserId, UserDTO adminDto,
            boolean setUsersAdminState) {

        if (!userService.ensuredUserExistance(adminDto)) {
            // Return user verification error
            return returnFailedUserVerification();
        }

        if (adminDto.getId().equals(targetUserId)) {
            log.logInfo("User %s tried to update their own admin state.", adminDto.getFirstName());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    Map.of("error", "Not possible for yourself.",
                            "errId", "CANT_DO_SELF"));
        }

        Optional<Group> group = groupRepository.findById(groupId);
        if (group.isEmpty()) {
            log.logInfo("User %s tried to set a users admin state for a group that doesn't exist.",
                    adminDto.getFirstName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "Group not found.",
                            "errId", "GROUP_NOT_FOUND"));
        }

        if (!group.get().getOwner().getKeycloakId().equals(adminDto.getId())) {
            log.logInfo("User %s tried to update a users admin state but is not the owner.", adminDto.getFirstName());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("error", "Can only be done by group owner.",
                            "errId", "NOT_THE_GROUP_OWNER"));
        }

        Optional<GroupMembership> membership = membershipRepository.findMembershipByGroupIdAndUserId(groupId,
                targetUserId);

        if (membership.isEmpty()) {
            log.logInfo("User %s tried to update a users admin state but that user is not a member.",
                    adminDto.getFirstName());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    Map.of("error", "The specified user is not a member of that group.",
                            "errId", "USER_NOT_A_GROUP_MEMBER"));
        }

        membership.get().setAdmin(setUsersAdminState);

        membershipRepository.save(membership.get());

        return ResponseEntity.ok(new GroupUserReturnDTO(
                membership.get().getUser().getKeycloakId(),
                membership.get().getUser().getFirstName(),
                membership.get().getUser().getLastName(),
                membership.get().isAdmin()));

    }

    public ResponseEntity<Object> removeUserFromGroup(UUID groupId, UUID targetUserId, UserDTO adminDto) {

        if (!userService.ensuredUserExistance(adminDto)) {
            // Return user verification error
            return returnFailedUserVerification();
        }

        Optional<Group> group = groupRepository.findById(groupId);
        if (group.isEmpty()) {
            log.logInfo("User %s tried to remove a users from a group that doesn't exist.",
                    adminDto.getFirstName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "Group not found.",
                            "errId", "GROUP_NOT_FOUND"));
        }

        Optional<GroupMembership> adminMembership = membershipRepository.findMembershipByGroupIdAndUserId(groupId,
                adminDto.getId());

        if (adminMembership.isEmpty()) {
            log.logInfo("User %s tried to remove a user from a group they are not a member of.",
                    adminDto.getFirstName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("error", "User is not a member of that group.",
                            "errId", "NOT_A_GROUP_MEMBER"));
        }

        if (!adminMembership.get().isAdmin()) {
            log.logInfo("User %s tried to remove a user but is not an admin.", adminDto.getFirstName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("error", "User is not an admin of that group.",
                            "errId", "NOT_A_GROUP_ADMIN"));
        }

        Optional<GroupMembership> targetMembership = membershipRepository.findMembershipByGroupIdAndUserId(groupId,
                targetUserId);

        if (targetMembership.isEmpty()) {
            log.logInfo("User %s tried to remove a users but that user is not a member.",
                    adminDto.getFirstName());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    Map.of("error", "The specified user is not a member of that group.",
                            "errId", "USER_NOT_A_GROUP_MEMBER"));
        }

        if (targetMembership.get().isAdmin() && !group.get().getOwner().getKeycloakId().equals(adminDto.getId())) {
            log.logInfo("User %s tried to remove an admin from a group but they are not the owner.",
                    adminDto.getFirstName());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("error", "Can only be done by group owner.",
                            "errId", "NOT_THE_GROUP_OWNER"));
        }

        membershipRepository.delete(targetMembership.get());

        return ResponseEntity.ok().body(Map.of());
    }

    /*
     * Helper
     */

    private ResponseEntity<Object> returnFailedUserVerification() {
        return ResponseEntity.internalServerError().body(
                Map.of("error", "User verification failed.",
                        "errId", "USER_VERIFICATION_FAILED"));
    }
}
