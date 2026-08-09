package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.endpoints.dto.common.ReturnListDTO;
import de.marsg.wishlist.endpoints.dto.memberships.ReturnMembershipDTO;
import de.marsg.wishlist.endpoints.dto.users.ReturnSimpleUserDTO;
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.helpers.PageParams;
import de.marsg.wishlist.jpa.entities.GroupEntity;
import de.marsg.wishlist.jpa.entities.GroupInviteEntity;
import de.marsg.wishlist.jpa.entities.GroupMembershipEntity;
import de.marsg.wishlist.jpa.entities.UserEntity;
import de.marsg.wishlist.jpa.repositories.GroupInviteRepository;
import de.marsg.wishlist.jpa.repositories.GroupMembershipRepository;
import de.marsg.wishlist.jpa.repositories.GroupWishlistShareRepository;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {

    private static final Logger log = LoggerFactory.getLogger(MembershipService.class);

    private final GroupMembershipRepository membershipRepository;
    private final GroupInviteRepository inviteRepository;
    private final GroupWishlistShareRepository shareRepository;
    private final UserRepository userRepository;
    private final GroupService groupService;
    private final AccessService accessService;

    public MembershipService(GroupMembershipRepository membershipRepository,
            GroupInviteRepository inviteRepository, GroupWishlistShareRepository shareRepository,
            UserRepository userRepository, GroupService groupService, AccessService accessService) {
        this.membershipRepository = membershipRepository;
        this.inviteRepository = inviteRepository;
        this.shareRepository = shareRepository;
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
    public ReturnListDTO<ReturnMembershipDTO> list(UUID userId, UUID groupId, Integer limit, Integer offset) {
        GroupEntity group = groupService.requireGroup(groupId);
        accessService.assertGroupMember(userId, group);
        Pageable pageable = PageParams.of(limit, offset, Sort.by(Sort.Direction.ASC, "id"));
        Page<GroupMembershipEntity> page = membershipRepository.findByGroupsId(groupId, pageable);
        Map<UUID, ReturnSimpleUserDTO> users = userRepository.findAllById(
                        page.getContent().stream().map(GroupMembershipEntity::getUsersId).toList())
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, ReturnSimpleUserDTO::from));
        return ReturnListDTO.of(page, page.getContent().stream()
                // memberships of soft-deleted users drop out
                .filter(membership -> users.containsKey(membership.getUsersId()))
                .map(membership -> ReturnMembershipDTO.from(membership,
                        users.get(membership.getUsersId()),
                        group.getUsersId().equals(membership.getUsersId())))
                .toList());
    }

    /** Multiple admins allowed; the LAST admin can neither be demoted (nor leave). */
    @Transactional
    public ReturnMembershipDTO setAdmin(UUID userId, UUID membershipId, boolean isAdmin) {
        GroupMembershipEntity membership = requireMembership(membershipId);
        GroupEntity group = groupService.requireGroup(membership.getGroupsId());
        accessService.assertGroupAdmin(userId, group);
        if (!isAdmin && membership.isAdmin() && isLastAdmin(group.getId())) {
            throw new ApiException(ErrorCode.LAST_ADMIN_CANNOT_BE_DEMOTED);
        }
        membership.setAdmin(isAdmin);
        UserEntity member = userRepository.findById(membership.getUsersId())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        return ReturnMembershipDTO.from(membership, ReturnSimpleUserDTO.from(member),
                group.getUsersId().equals(membership.getUsersId()));
    }

    /**
     * Removal by a group admin, or the member themself leaving. An admin can leave
     * as long as at least one other admin remains — only the last admin cannot.
     */
    @Transactional
    public void remove(UUID userId, UUID membershipId) {
        GroupMembershipEntity membership = requireMembership(membershipId);
        GroupEntity group = groupService.requireGroup(membership.getGroupsId());
        if (!membership.getUsersId().equals(userId)) {
            accessService.assertGroupAdmin(userId, group);
        }
        if (membership.isAdmin() && isLastAdmin(group.getId())) {
            throw new ApiException(ErrorCode.LAST_ADMIN_CANNOT_LEAVE);
        }
        membershipRepository.delete(membership);
        // a wishlist must never stay shared with a group its owner is no longer part of
        shareRepository.deleteAll(
                shareRepository.findSharesOfUserInGroup(membership.getUsersId(), group.getId()));
        if (group.getUsersId().equals(membership.getUsersId())) {
            transferCreator(group, membership.getUsersId());
        }
        log.info("User {} removed membership {} from group {}", userId, membershipId, group.getId());
    }

    /**
     * The creator column is a NOT NULL FK that cascades on account purge; hand it to a
     * remaining admin when the creator leaves so the group survives their account deletion.
     */
    private void transferCreator(GroupEntity group, UUID leavingUserId) {
        membershipRepository.findByGroupsId(group.getId()).stream()
                .filter(remaining -> !remaining.getUsersId().equals(leavingUserId))
                .filter(GroupMembershipEntity::isAdmin)
                .findFirst()
                .ifPresent(newCreator -> {
                    group.transferCreatorTo(newCreator.getUsersId());
                    log.info("Transferred creator of group {} to user {}",
                            group.getId(), newCreator.getUsersId());
                });
    }

    private boolean isLastAdmin(UUID groupId) {
        return membershipRepository.countByGroupsIdAndIsAdminTrue(groupId) <= 1;
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
