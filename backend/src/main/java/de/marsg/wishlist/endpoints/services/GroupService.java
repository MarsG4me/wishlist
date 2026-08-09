package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.endpoints.dto.groups.CreateUpdateGroupDTO;
import de.marsg.wishlist.endpoints.dto.groups.ReturnGroupDTO;
import de.marsg.wishlist.endpoints.dto.groups.ReturnSharedWishlistDTO;
import de.marsg.wishlist.endpoints.dto.users.ReturnSimpleUserDTO;
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
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
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public GroupService(GroupRepository groupRepository, GroupMembershipRepository membershipRepository,
            GroupWishlistShareRepository shareRepository, WishlistRepository wishlistRepository,
            UserRepository userRepository, AccessService accessService, LimitService limitService) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.shareRepository = shareRepository;
        this.wishlistRepository = wishlistRepository;
        this.userRepository = userRepository;
        this.accessService = accessService;
        this.limitService = limitService;
    }

    @Transactional(readOnly = true)
    public List<ReturnGroupDTO> listOwn(UUID userId) {
        return groupRepository.findGroupsOfUser(userId).stream()
                .map(ReturnGroupDTO::from)
                .toList();
    }

    @Transactional
    public ReturnGroupDTO create(UUID userId, UserTier tier, CreateUpdateGroupDTO dto) {
        limitService.assertCanCreateGroup(userId, tier);
        GroupEntity group = groupRepository.save(new GroupEntity(userId, dto.name(), dto.description()));
        membershipRepository.save(new GroupMembershipEntity(group.getId(), userId, null, true));
        log.info("User {} created group {}", userId, group.getId());
        return ReturnGroupDTO.from(group);
    }

    @Transactional(readOnly = true)
    public ReturnGroupDTO get(UUID userId, UUID groupId) {
        GroupEntity group = requireGroup(groupId);
        accessService.assertGroupMember(userId, group);
        return ReturnGroupDTO.from(group);
    }

    @Transactional
    public ReturnGroupDTO update(UUID userId, UUID groupId, CreateUpdateGroupDTO dto) {
        GroupEntity group = requireGroup(groupId);
        accessService.assertGroupAdmin(userId, group);
        group.setName(dto.name());
        group.setDescription(dto.description());
        return ReturnGroupDTO.from(group);
    }

    @Transactional
    public void softDelete(UUID userId, UUID groupId) {
        GroupEntity group = requireGroup(groupId);
        accessService.assertGroupAdmin(userId, group);
        group.markDeleted();
        log.info("User {} soft-deleted group {}", userId, groupId);
    }

    @Transactional(readOnly = true)
    public List<ReturnSharedWishlistDTO> listShares(UUID userId, UUID groupId) {
        GroupEntity group = requireGroup(groupId);
        accessService.assertGroupMember(userId, group);
        List<GroupWishlistShareEntity> shares = shareRepository.findByGroupsId(groupId);
        List<WishlistEntity> wishlists = wishlistRepository.findAllById(
                shares.stream().map(GroupWishlistShareEntity::getWishlistsId).toList());
        Map<UUID, ReturnSimpleUserDTO> owners = userRepository.findAllById(
                        wishlists.stream().map(WishlistEntity::getUsersId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, ReturnSimpleUserDTO::from));
        Map<UUID, WishlistEntity> byId = wishlists.stream()
                .collect(Collectors.toMap(WishlistEntity::getId, Function.identity()));
        return shares.stream()
                .map(share -> byId.get(share.getWishlistsId()))
                // soft-deleted wishlists drop out of findAllById
                .filter(wishlist -> wishlist != null)
                .map(wishlist -> new ReturnSharedWishlistDTO(wishlist.getId(), wishlist.getName(),
                        owners.get(wishlist.getUsersId())))
                .toList();
    }

    /** Sharing requires owning the wishlist AND being a member of the target group. */
    @Transactional
    public void share(UUID userId, UUID groupId, UUID wishlistId) {
        GroupEntity group = requireGroup(groupId);
        accessService.assertGroupMember(userId, group);
        WishlistEntity wishlist = wishlistRepository.findById(wishlistId)
                .orElseThrow(() -> new ApiException(ErrorCode.WISHLIST_NOT_FOUND));
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
        GroupEntity group = requireGroup(groupId);
        GroupWishlistShareEntity share = shareRepository.findByGroupsIdAndWishlistsId(groupId, wishlistId)
                .orElseThrow(() -> new ApiException(ErrorCode.SHARE_NOT_FOUND));
        boolean isWishlistOwner = wishlistRepository.findById(wishlistId)
                .map(wishlist -> wishlist.getUsersId().equals(userId))
                .orElse(false);
        if (!isWishlistOwner) {
            accessService.assertGroupAdmin(userId, group);
        }
        shareRepository.delete(share);
        log.info("User {} removed share of wishlist {} from group {}", userId, wishlistId, groupId);
    }

    GroupEntity requireGroup(UUID groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ApiException(ErrorCode.GROUP_NOT_FOUND));
    }
}
