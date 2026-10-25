package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.endpoints.dto.common.ReturnListDTO;
import de.marsg.wishlist.endpoints.dto.wishlists.ReturnWishlistDTO;
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.helpers.PageParams;
import de.marsg.wishlist.jpa.entities.UserTier;
import de.marsg.wishlist.jpa.entities.WishlistEntity;
import de.marsg.wishlist.jpa.repositories.WishlistRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WishlistService {

    private static final Logger log = LoggerFactory.getLogger(WishlistService.class);

    private final WishlistRepository wishlistRepository;
    private final AccessService accessService;
    private final LimitService limitService;

    public WishlistService(WishlistRepository wishlistRepository, AccessService accessService,
            LimitService limitService) {
        this.wishlistRepository = wishlistRepository;
        this.accessService = accessService;
        this.limitService = limitService;
    }

    @Transactional(readOnly = true)
    public ReturnListDTO<ReturnWishlistDTO> listOwn(UUID userId, Integer limit, Integer offset) {
        // newest first: UUIDv7 ids sort by creation time
        Pageable pageable = PageParams.of(limit, offset, Sort.by(Sort.Direction.DESC, "id"));
        return ReturnListDTO.of(wishlistRepository.findByUsersId(userId, pageable), ReturnWishlistDTO::from);
    }

    @Transactional
    public ReturnWishlistDTO create(UUID userId, UserTier tier, String name) {
        limitService.assertCanCreateWishlist(userId, tier);
        WishlistEntity wishlist = wishlistRepository.save(new WishlistEntity(userId, name));
        log.info("User {} created wishlist {}", userId, wishlist.getId());
        return ReturnWishlistDTO.from(wishlist);
    }

    @Transactional(readOnly = true)
    public ReturnWishlistDTO get(UUID userId, UUID wishlistId) {
        WishlistEntity wishlist = requireWishlist(wishlistId);
        accessService.assertCanViewWishlist(userId, wishlist);
        return ReturnWishlistDTO.from(wishlist);
    }

    @Transactional
    public ReturnWishlistDTO rename(UUID userId, UUID wishlistId, String name) {
        WishlistEntity wishlist = requireWishlist(wishlistId);
        accessService.assertCanViewWishlist(userId, wishlist);
        accessService.assertWishlistOwner(userId, wishlist);
        wishlist.setName(name);
        return ReturnWishlistDTO.from(wishlist);
    }

    @Transactional
    public void softDelete(UUID userId, UUID wishlistId) {
        WishlistEntity wishlist = requireWishlist(wishlistId);
        accessService.assertCanViewWishlist(userId, wishlist);
        accessService.assertWishlistOwner(userId, wishlist);
        wishlist.markDeleted();
        log.info("User {} soft-deleted wishlist {}", userId, wishlistId);
    }

    WishlistEntity requireWishlist(UUID wishlistId) {
        return wishlistRepository.findById(wishlistId)
                .orElseThrow(() -> new ApiException(ErrorCode.WISHLIST_NOT_FOUND));
    }
}
