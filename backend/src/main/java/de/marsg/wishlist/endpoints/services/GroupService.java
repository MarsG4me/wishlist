package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.endpoints.dto.common.ReturnListDTO;
import de.marsg.wishlist.endpoints.dto.groups.CreateUpdateGroupDTO;
import de.marsg.wishlist.endpoints.dto.groups.ReturnGroupDTO;
import de.marsg.wishlist.endpoints.dto.groups.ReturnSharedWishlistDTO;
import de.marsg.wishlist.endpoints.dto.users.ReturnSimpleUserDTO;
import de.marsg.wishlist.endpoints.dto.wishlists.ReturnWishlistDTO;
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.helpers.PageParams;
import de.marsg.wishlist.jpa.entities.GroupEntity;
import de.marsg.wishlist.jpa.entities.GroupMembershipEntity;
import de.marsg.wishlist.jpa.entities.GroupWishlistShareEntity;
import de.marsg.wishlist.jpa.entities.UserEntity;
import de.marsg.wishlist.jpa.entities.UserTier;
import de.marsg.wishlist.jpa.entities.WishlistEntity;
import de.marsg.wishlist.jpa.repositories.GroupMembershipRepository;
import de.marsg.wishlist.jpa.repositories.GroupRepository;
import de.marsg.wishlist.jpa.repositories.GroupWishlistShareRepository;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import de.marsg.wishlist.jpa.repositories.WishlistRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    private final GroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final GroupWishlistShareRepository shareRepository;
    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;
    private final AccessService accessService;
    private final LimitService limitService;
    private final ClaimService claimService;

    public GroupService(GroupRepository groupRepository, GroupMembershipRepository membershipRepository,
            GroupWishlistShareRepository shareRepository, WishlistRepository wishlistRepository,
            UserRepository userRepository, AccessService accessService, LimitService limitService,
            ClaimService claimService) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.shareRepository = shareRepository;
        this.wishlistRepository = wishlistRepository;
        this.userRepository = userRepository;
        this.accessService = accessService;
        this.limitService = limitService;
        this.claimService = claimService;
    }

    @Transactional(readOnly = true)
    public ReturnListDTO<ReturnGroupDTO> listOwn(UUID userId, Integer limit, Integer offset) {
        Pageable pageable = PageParams.of(limit, offset, Sort.by(Sort.Direction.DESC, "id"));
        // one query returns the groups together with the requester's own admin flag
        Page<Object[]> page = groupRepository.findGroupsOfUserWithRole(userId, pageable);
        return ReturnListDTO.of(page, page.getContent().stream()
                .map(row -> ReturnGroupDTO.from((GroupEntity) row[0], (Boolean) row[1]))
                .toList());
    }

    @Transactional
    public ReturnGroupDTO create(UUID userId, UserTier tier, CreateUpdateGroupDTO dto) {
        limitService.assertCanCreateGroup(userId, tier);
        GroupEntity group = groupRepository.save(new GroupEntity(userId, dto.name(), dto.description()));
        membershipRepository.save(new GroupMembershipEntity(group.getId(), userId, null, true));
        log.info("User {} created group {}", userId, group.getId());
        return ReturnGroupDTO.from(group, true);
    }

    @Transactional(readOnly = true)
    public ReturnGroupDTO get(UUID userId, UUID groupId) {
        GroupEntity group = requireGroupAsMember(userId, groupId);
        return ReturnGroupDTO.from(group, accessService.isGroupAdmin(userId, groupId));
    }

    @Transactional
    public ReturnGroupDTO update(UUID userId, UUID groupId, CreateUpdateGroupDTO dto) {
        GroupEntity group = requireGroupAsMember(userId, groupId);
        accessService.assertGroupAdmin(userId, group);
        group.setName(dto.name());
        group.setDescription(dto.description());
        return ReturnGroupDTO.from(group, true);
    }

    /** Deleting the group ends every share it carried, so the claims behind them go too. */
    @Transactional
    public void softDelete(UUID userId, UUID groupId) {
        GroupEntity group = requireGroupAsMember(userId, groupId);
        accessService.assertGroupAdmin(userId, group);
        List<UUID> sharedWishlists = shareRepository.findByGroupsId(groupId).stream()
                .map(GroupWishlistShareEntity::getWishlistsId)
                .toList();
        group.markDeleted();
        groupRepository.flush();
        claimService.purgeParticipationsWithoutAccess(sharedWishlists);
        log.info("User {} soft-deleted group {}", userId, groupId);
    }

    @Transactional(readOnly = true)
    public ReturnListDTO<ReturnSharedWishlistDTO> listShares(UUID userId, UUID groupId, Integer limit,
            Integer offset) {
        GroupEntity group = requireGroupAsMember(userId, groupId);
        Pageable pageable = PageParams.of(limit, offset, Sort.by(Sort.Direction.DESC, "id"));
        Page<GroupWishlistShareEntity> page = shareRepository.findByGroupsId(groupId, pageable);
        List<GroupWishlistShareEntity> shares = page.getContent();
        List<WishlistEntity> wishlists = wishlistRepository.findAllById(
                shares.stream().map(GroupWishlistShareEntity::getWishlistsId).toList());
        Map<UUID, ReturnSimpleUserDTO> owners = userRepository.findAllById(
                        wishlists.stream().map(WishlistEntity::getUsersId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, ReturnSimpleUserDTO::from));
        Map<UUID, WishlistEntity> byId = wishlists.stream()
                .collect(Collectors.toMap(WishlistEntity::getId, Function.identity()));
        return ReturnListDTO.of(page, shares.stream()
                .map(share -> byId.get(share.getWishlistsId()))
                // soft-deleted wishlists drop out of findAllById
                .filter(Objects::nonNull)
                .map(wishlist -> new ReturnSharedWishlistDTO(wishlist.getId(), wishlist.getName(),
                        owners.get(wishlist.getUsersId())))
                .toList());
    }

    /** Own wishlists that can still be shared here; the "not yet shared" filter runs in SQL. */
    @Transactional(readOnly = true)
    public ReturnListDTO<ReturnWishlistDTO> listShareableWishlists(UUID userId, UUID groupId,
            Integer limit, Integer offset) {
        GroupEntity group = requireGroupAsMember(userId, groupId);
        Pageable pageable = PageParams.of(limit, offset, Sort.by(Sort.Direction.DESC, "id"));
        return ReturnListDTO.of(wishlistRepository.findShareableIntoGroup(userId, groupId, pageable),
                ReturnWishlistDTO::from);
    }

    /** Sharing requires owning the wishlist AND being a member of the target group. */
    @Transactional
    public void share(UUID userId, UUID groupId, UUID wishlistId) {
        GroupEntity group = requireGroupAsMember(userId, groupId);
        WishlistEntity wishlist = wishlistRepository.findById(wishlistId)
                .orElseThrow(() -> new ApiException(ErrorCode.WISHLIST_NOT_FOUND));
        accessService.assertCanViewWishlist(userId, wishlist);
        accessService.assertWishlistOwner(userId, wishlist);
        if (shareRepository.existsByGroupsIdAndWishlistsId(groupId, wishlistId)) {
            throw new ApiException(ErrorCode.WISHLIST_ALREADY_SHARED);
        }
        shareRepository.save(new GroupWishlistShareEntity(groupId, wishlistId));
        log.info("User {} shared wishlist {} into group {}", userId, wishlistId, groupId);
    }

    /** Unshare: the wishlist owner or a group admin. */
    @Transactional
    public void unshare(UUID userId, UUID groupId, UUID wishlistId) {
        GroupEntity group = requireGroupAsMember(userId, groupId);
        GroupWishlistShareEntity share = shareRepository.findByGroupsIdAndWishlistsId(groupId, wishlistId)
                .orElseThrow(() -> new ApiException(ErrorCode.SHARE_NOT_FOUND));
        boolean isWishlistOwner = wishlistRepository.findById(wishlistId)
                .map(wishlist -> wishlist.getUsersId().equals(userId))
                .orElse(false);
        if (!isWishlistOwner) {
            accessService.assertGroupAdmin(userId, group);
        }
        shareRepository.delete(share);
        shareRepository.flush();
        // members of this group lose sight of the wishlist unless another share still covers it
        claimService.purgeParticipationsWithoutAccess(List.of(wishlistId));
        log.info("User {} removed share of wishlist {} from group {}", userId, wishlistId, groupId);
    }

    /** Group lookup for a caller who must be a member; a non-member sees GROUP_NOT_FOUND. */
    private GroupEntity requireGroupAsMember(UUID userId, UUID groupId) {
        GroupEntity group = requireGroup(groupId);
        if (!membershipRepository.existsByGroupsIdAndUsersId(groupId, userId)) {
            throw new ApiException(ErrorCode.GROUP_NOT_FOUND);
        }
        return group;
    }

    /** Live (not soft-deleted) group, if any. */
    Optional<GroupEntity> findGroup(UUID groupId) {
        return groupRepository.findById(groupId);
    }

    GroupEntity requireGroup(UUID groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ApiException(ErrorCode.GROUP_NOT_FOUND));
    }
}
