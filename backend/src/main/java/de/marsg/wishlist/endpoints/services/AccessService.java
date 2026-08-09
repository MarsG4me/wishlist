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
            throw new ApiException(ErrorCode.NOT_GROUP_MEMBER);
        }
    }

    /** Group owner or membership with admin flag. */
    public void assertGroupAdmin(UUID userId, GroupEntity group) {
        if (group.getUsersId().equals(userId)) {
            return;
        }
        boolean isAdmin = membershipRepository.findByGroupsIdAndUsersId(group.getId(), userId)
                .map(GroupMembershipEntity::isAdmin)
                .orElse(false);
        if (!isAdmin) {
            throw new ApiException(ErrorCode.NOT_GROUP_ADMIN);
        }
    }

    public void assertGroupOwner(UUID userId, GroupEntity group) {
        if (!group.getUsersId().equals(userId)) {
            throw new ApiException(ErrorCode.NOT_GROUP_OWNER);
        }
    }
}
