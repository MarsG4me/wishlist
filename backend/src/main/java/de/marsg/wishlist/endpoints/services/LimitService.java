package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.jpa.entities.UserTier;
import de.marsg.wishlist.jpa.repositories.GroupRepository;
import de.marsg.wishlist.jpa.repositories.WishRepository;
import de.marsg.wishlist.jpa.repositories.WishlistRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Business limits; PREMIUM doubles every limit. */
@Service
public class LimitService {

    static final int MAX_OWNED_GROUPS = 10;
    static final int MAX_WISHLISTS = 10;
    static final int MAX_WISHES_PER_LIST = 100;

    private final GroupRepository groupRepository;
    private final WishlistRepository wishlistRepository;
    private final WishRepository wishRepository;

    public LimitService(GroupRepository groupRepository, WishlistRepository wishlistRepository,
            WishRepository wishRepository) {
        this.groupRepository = groupRepository;
        this.wishlistRepository = wishlistRepository;
        this.wishRepository = wishRepository;
    }

    public void assertCanCreateGroup(UUID userId, UserTier tier) {
        if (groupRepository.countByUsersId(userId) >= limitFor(MAX_OWNED_GROUPS, tier)) {
            throw new ApiException(ErrorCode.GROUP_LIMIT_REACHED);
        }
    }

    public void assertCanCreateWishlist(UUID userId, UserTier tier) {
        if (wishlistRepository.countByUsersId(userId) >= limitFor(MAX_WISHLISTS, tier)) {
            throw new ApiException(ErrorCode.WISHLIST_LIMIT_REACHED);
        }
    }

    public void assertCanAddWish(UUID wishlistId, UserTier ownerTier) {
        if (wishRepository.countByWishlistsId(wishlistId) >= limitFor(MAX_WISHES_PER_LIST, ownerTier)) {
            throw new ApiException(ErrorCode.WISH_LIMIT_REACHED);
        }
    }

    private long limitFor(int base, UserTier tier) {
        return tier == UserTier.PREMIUM ? base * 2L : base;
    }
}
