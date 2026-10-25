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
import de.marsg.wishlist.jpa.entities.GroupWishlistShareEntity;
import de.marsg.wishlist.jpa.entities.UserEntity;
import de.marsg.wishlist.jpa.repositories.GroupInviteRepository;
import de.marsg.wishlist.jpa.repositories.GroupMembershipRepository;
import de.marsg.wishlist.jpa.repositories.GroupWishlistShareRepository;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import java.util.Comparator;
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
    private final ClaimService claimService;

    public MembershipService(GroupMembershipRepository membershipRepository,
            GroupInviteRepository inviteRepository, GroupWishlistShareRepository shareRepository,
            UserRepository userRepository, GroupService groupService, AccessService accessService,
            ClaimService claimService) {
        this.membershipRepository = membershipRepository;
        this.inviteRepository = inviteRepository;
        this.shareRepository = shareRepository;
        this.userRepository = userRepository;
        this.groupService = groupService;
        this.accessService = accessService;
        this.claimService = claimService;
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
        if (!membershipRepository.existsByGroupsIdAndUsersId(groupId, userId)) {
            throw new ApiException(ErrorCode.GROUP_NOT_FOUND);
        }
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
                        membership.getUsersId().equals(group.getUsersId())))
                .toList());
    }

    /** Multiple admins allowed; the LAST admin can neither be demoted (nor leave). */
    @Transactional
    public ReturnMembershipDTO setAdmin(UUID userId, UUID membershipId, boolean isAdmin) {
        GroupMembershipEntity membership = requireMembership(membershipId);
        GroupEntity group = lockGroup(membership.getGroupsId());
        assertAdminHidingMembership(userId, group);
        if (!isAdmin && membership.isAdmin() && isLastAdmin(group.getId())) {
            throw new ApiException(ErrorCode.LAST_ADMIN_CANNOT_BE_DEMOTED);
        }
        membership.setAdmin(isAdmin);
        UserEntity member = userRepository.findById(membership.getUsersId())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        return ReturnMembershipDTO.from(membership, ReturnSimpleUserDTO.from(member),
                membership.getUsersId().equals(group.getUsersId()));
    }

    /**
     * Removal by a group admin, or the member themself leaving. An admin can leave
     * as long as at least one other admin remains — only the last admin cannot.
     */
    @Transactional
    public void remove(UUID userId, UUID membershipId) {
        GroupMembershipEntity membership = requireMembership(membershipId);
        GroupEntity group = lockGroup(membership.getGroupsId());
        if (!membership.getUsersId().equals(userId)) {
            assertAdminHidingMembership(userId, group);
        }
        if (membership.isAdmin() && isLastAdmin(group.getId())) {
            throw new ApiException(ErrorCode.LAST_ADMIN_CANNOT_LEAVE);
        }
        detach(membership, group);
        if (membership.getUsersId().equals(group.getUsersId())) {
            transferCreator(group, membership.getUsersId());
        }
        log.info("User {} removed membership {} from group {}", userId, membershipId, group.getId());
    }

    /**
     * Account deletion: leaves every group. A creator or last admin hands creator role and
     * admin to another admin, else the oldest member; a group left empty is soft-deleted.
     */
    @Transactional
    public void removeAllMembershipsOf(UUID userId) {
        // ascending group order: two accounts leaving shared groups cannot lock them crosswise
        List<GroupMembershipEntity> owned = membershipRepository.findByUsersId(userId).stream()
                .sorted(Comparator.comparing(GroupMembershipEntity::getGroupsId)).toList();
        for (GroupMembershipEntity own : owned) {
            GroupEntity group = membershipRepository.findGroupForUpdate(own.getGroupsId()).orElse(null);
            if (group == null) {
                continue;
            }
            List<GroupMembershipEntity> others = membershipRepository.findByGroupsId(group.getId()).stream()
                    .filter(member -> !member.getUsersId().equals(userId))
                    .sorted(Comparator.comparing(GroupMembershipEntity::getId))
                    .toList();
            if (others.isEmpty()) {
                softDeleteEmptyGroup(own, group);
                continue;
            }
            boolean isCreator = userId.equals(group.getUsersId());
            boolean lastAdmin = own.isAdmin() && others.stream().noneMatch(GroupMembershipEntity::isAdmin);
            GroupMembershipEntity successor = others.stream().filter(GroupMembershipEntity::isAdmin)
                    .findFirst().orElse(others.get(0));
            if (isCreator || lastAdmin) {
                successor.setAdmin(true);
            }
            detach(own, group);
            if (isCreator) {
                group.transferCreatorTo(successor.getUsersId());
            }
            log.info("User {} left group {} on account deletion", userId, group.getId());
        }
    }

    /** Nobody is left to hand the group to: delete it, which also ends every share it carried. */
    private void softDeleteEmptyGroup(GroupMembershipEntity own, GroupEntity group) {
        List<UUID> sharedWishlists = shareRepository.findByGroupsId(group.getId()).stream()
                .map(GroupWishlistShareEntity::getWishlistsId)
                .toList();
        membershipRepository.delete(own);
        group.markDeleted();
        membershipRepository.flush();
        claimService.purgeParticipationsWithoutAccess(sharedWishlists);
        log.info("Soft-deleted group {} left without members", group.getId());
    }

    /** Deletes the membership and the leaver's shares in the group, then revokes stale claims. */
    private void detach(GroupMembershipEntity membership, GroupEntity group) {
        // taken before the change: the leaver loses sight of all of them, and the rest of
        // the group loses sight of the leaver's own ones
        List<UUID> sharedWishlists = shareRepository.findByGroupsId(group.getId()).stream()
                .map(GroupWishlistShareEntity::getWishlistsId)
                .toList();
        membershipRepository.delete(membership);
        // a wishlist must never stay shared with a group its owner is no longer part of
        shareRepository.deleteAll(
                shareRepository.findSharesOfUserInGroup(membership.getUsersId(), group.getId()));
        shareRepository.flush();
        claimService.purgeParticipationsWithoutAccess(sharedWishlists);
    }

    /** Row lock on the group so concurrent admin changes cannot both pass the last-admin rule. */
    private GroupEntity lockGroup(UUID groupId) {
        return membershipRepository.findGroupForUpdate(groupId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEMBERSHIP_NOT_FOUND));
    }

    /** Non-members get the unknown-id answer, so membership ids cannot be probed. */
    private void assertAdminHidingMembership(UUID userId, GroupEntity group) {
        if (!membershipRepository.existsByGroupsIdAndUsersId(group.getId(), userId)) {
            throw new ApiException(ErrorCode.MEMBERSHIP_NOT_FOUND);
        }
        accessService.assertGroupAdmin(userId, group);
    }

    /**
     * The creator column is nullable now, but a creator who left should still hand it to a
     * remaining admin so the group keeps an informational owner.
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
                membership.getUsersId().equals(group.getUsersId()));
    }

    private GroupMembershipEntity requireMembership(UUID membershipId) {
        return membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEMBERSHIP_NOT_FOUND));
    }
}
