package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.jpa.entities.GroupEntity;
import de.marsg.wishlist.jpa.entities.GroupMembershipEntity;
import de.marsg.wishlist.jpa.entities.WishlistEntity;
import de.marsg.wishlist.jpa.repositories.GroupMembershipRepository;
import de.marsg.wishlist.jpa.repositories.GroupWishlistShareRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Central authorization checks shared by all services. */
@Service
public class AccessService {

    private final GroupMembershipRepository membershipRepository;
    private final GroupWishlistShareRepository shareRepository;

    public AccessService(GroupMembershipRepository membershipRepository,
            GroupWishlistShareRepository shareRepository) {
        this.membershipRepository = membershipRepository;
        this.shareRepository = shareRepository;
    }

    public boolean isWishlistOwner(UUID userId, WishlistEntity wishlist) {
        return wishlist.getUsersId().equals(userId);
    }

    public void assertWishlistOwner(UUID userId, WishlistEntity wishlist) {
        if (!isWishlistOwner(userId, wishlist)) {
            throw new ApiException(ErrorCode.NOT_WISHLIST_OWNER);
        }
    }

    /** Owner passes; a viewer gets NOT_WISHLIST_OWNER; everyone else {@code hidden}, so ids do not leak. */
    public void assertWishlistOwnerOrHide(UUID userId, WishlistEntity wishlist, ErrorCode hidden) {
        if (isWishlistOwner(userId, wishlist)) {
            return;
        }
        throw new ApiException(canViewAsSharedWishlist(userId, wishlist.getId())
                ? ErrorCode.NOT_WISHLIST_OWNER : hidden);
    }

    /** Admin passes; a plain member gets NOT_GROUP_ADMIN; a non-member GROUP_NOT_FOUND. */
    public void assertGroupAdminOrHide(UUID userId, GroupEntity group) {
        if (isGroupAdmin(userId, group.getId())) {
            return;
        }
        throw new ApiException(membershipRepository.existsByGroupsIdAndUsersId(group.getId(), userId)
                ? ErrorCode.NOT_GROUP_ADMIN : ErrorCode.GROUP_NOT_FOUND);
    }

    /** Owner, or member of at least one group the wishlist is shared into. */
    public void assertCanViewWishlist(UUID userId, WishlistEntity wishlist) {
        if (!isWishlistOwner(userId, wishlist)
                && !shareRepository.existsShareGivingUserAccess(userId, wishlist.getId())) {
            throw new ApiException(ErrorCode.WISHLIST_NOT_FOUND);
        }
    }

    public boolean canViewAsSharedWishlist(UUID userId, UUID wishlistId) {
        return shareRepository.existsShareGivingUserAccess(userId, wishlistId);
    }

    public void assertGroupMember(UUID userId, GroupEntity group) {
        if (!membershipRepository.existsByGroupsIdAndUsersId(group.getId(), userId)) {
            throw new ApiException(ErrorCode.GROUP_NOT_FOUND);
        }
    }

    /** Boolean permission query — the caller's membership row, never a member list scan. */
    public boolean isGroupAdmin(UUID userId, UUID groupId) {
        return membershipRepository.existsByGroupsIdAndUsersIdAndIsAdminTrue(groupId, userId);
    }

    public void assertGroupAdmin(UUID userId, GroupEntity group) {
        if (!isGroupAdmin(userId, group.getId())) {
            throw new ApiException(ErrorCode.NOT_GROUP_ADMIN);
        }
    }

    /**
     * Whether the requester may learn who this user is. A wishlist can be shared into
     * several groups, so co-claimers are not automatically group mates — and identity is
     * bound to the shared group everywhere else too (see {@code UserService#getOtherUser}).
     */
    public boolean mayIdentify(UUID requesterId, UUID otherId) {
        return requesterId.equals(otherId)
                || membershipRepository.existsGroupWithBothUsers(requesterId, otherId);
    }

}
