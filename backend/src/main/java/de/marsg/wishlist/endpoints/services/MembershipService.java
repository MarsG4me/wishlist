package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.endpoints.dto.memberships.ReturnMembershipDTO;
import de.marsg.wishlist.endpoints.dto.users.ReturnSimpleUserDTO;
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.jpa.entities.GroupEntity;
import de.marsg.wishlist.jpa.entities.GroupInviteEntity;
import de.marsg.wishlist.jpa.entities.GroupMembershipEntity;
import de.marsg.wishlist.jpa.entities.UserEntity;
import de.marsg.wishlist.jpa.repositories.GroupInviteRepository;
import de.marsg.wishlist.jpa.repositories.GroupMembershipRepository;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {

    private static final Logger log = LoggerFactory.getLogger(MembershipService.class);

    private final GroupMembershipRepository membershipRepository;
    private final GroupInviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final GroupService groupService;
    private final AccessService accessService;

    public MembershipService(GroupMembershipRepository membershipRepository,
            GroupInviteRepository inviteRepository, UserRepository userRepository,
            GroupService groupService, AccessService accessService) {
        this.membershipRepository = membershipRepository;
        this.inviteRepository = inviteRepository;
        this.userRepository = userRepository;
        this.groupService = groupService;
        this.accessService = accessService;
    }

    /** Redeems an invite code; the atomic use_count increment blocks racing over-joins. */
    @Transactional
    public ReturnMembershipDTO join(UUID userId, String code) {
        GroupInviteEntity invite = inviteRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(ErrorCode.INVITE_NOT_FOUND));
        if (invite.isRevoked()) {
            throw new ApiException(ErrorCode.INVITE_REVOKED);
        }
        if (invite.isExpired()) {
            throw new ApiException(ErrorCode.INVITE_EXPIRED);
        }
        GroupEntity group = groupService.requireGroup(invite.getGroupsId());
        if (membershipRepository.existsByGroupsIdAndUsersId(group.getId(), userId)) {
            throw new ApiException(ErrorCode.ALREADY_MEMBER);
        }
        if (inviteRepository.consumeUse(invite.getId()) == 0) {
            throw new ApiException(ErrorCode.INVITE_EXHAUSTED);
        }
        GroupMembershipEntity membership = membershipRepository.save(
                new GroupMembershipEntity(group.getId(), userId, invite.getId(), false));
        log.info("User {} joined group {} via invite {}", userId, group.getId(), invite.getId());
        return toDto(membership, group);
    }

    @Transactional(readOnly = true)
    public List<ReturnMembershipDTO> list(UUID userId, UUID groupId) {
        GroupEntity group = groupService.requireGroup(groupId);
        accessService.assertGroupMember(userId, group);
        List<GroupMembershipEntity> memberships = membershipRepository.findByGroupsId(groupId);
        Map<UUID, ReturnSimpleUserDTO> users = userRepository.findAllById(
                        memberships.stream().map(GroupMembershipEntity::getUsersId).toList())
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, ReturnSimpleUserDTO::from));
        return memberships.stream()
                // memberships of soft-deleted users drop out
                .filter(membership -> users.containsKey(membership.getUsersId()))
                .map(membership -> ReturnMembershipDTO.from(membership,
                        users.get(membership.getUsersId()),
                        group.getUsersId().equals(membership.getUsersId())))
                .toList();
    }

    /** Owner's membership is immutable — the owner is always admin. */
    @Transactional
    public ReturnMembershipDTO setAdmin(UUID userId, UUID membershipId, boolean isAdmin) {
        GroupMembershipEntity membership = requireMembership(membershipId);
        GroupEntity group = groupService.requireGroup(membership.getGroupsId());
        accessService.assertGroupAdmin(userId, group);
        if (group.getUsersId().equals(membership.getUsersId())) {
            throw new ApiException(ErrorCode.OWNER_MEMBERSHIP_IMMUTABLE);
        }
        membership.setAdmin(isAdmin);
        UserEntity member = userRepository.findById(membership.getUsersId())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        return ReturnMembershipDTO.from(membership, ReturnSimpleUserDTO.from(member), false);
    }

    /** Removal by a group admin, or the member themself leaving. The owner can never leave. */
    @Transactional
    public void remove(UUID userId, UUID membershipId) {
        GroupMembershipEntity membership = requireMembership(membershipId);
        GroupEntity group = groupService.requireGroup(membership.getGroupsId());
        if (group.getUsersId().equals(membership.getUsersId())) {
            throw new ApiException(ErrorCode.OWNER_CANNOT_LEAVE);
        }
        if (!membership.getUsersId().equals(userId)) {
            accessService.assertGroupAdmin(userId, group);
        }
        membershipRepository.delete(membership);
        log.info("User {} removed membership {} from group {}", userId, membershipId, group.getId());
    }

    private ReturnMembershipDTO toDto(GroupMembershipEntity membership, GroupEntity group) {
        UserEntity member = userRepository.findById(membership.getUsersId())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        return ReturnMembershipDTO.from(membership, ReturnSimpleUserDTO.from(member),
                group.getUsersId().equals(membership.getUsersId()));
    }

    private GroupMembershipEntity requireMembership(UUID membershipId) {
        return membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEMBERSHIP_NOT_FOUND));
    }
}
