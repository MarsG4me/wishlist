package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.endpoints.dto.common.ReturnListDTO;
import de.marsg.wishlist.endpoints.dto.users.ReturnSimpleUserDTO;
import de.marsg.wishlist.endpoints.dto.wishes.CreateWishDTO;
import de.marsg.wishlist.endpoints.dto.wishes.ReturnOwnWishDTO;
import de.marsg.wishlist.endpoints.dto.wishes.ReturnSharedWishDTO;
import de.marsg.wishlist.endpoints.dto.wishes.ReturnUpdatedWishDTO;
import de.marsg.wishlist.endpoints.dto.wishes.ReturnWishClaimDTO;
import de.marsg.wishlist.endpoints.dto.wishes.UpdateWishDTO;
import de.marsg.wishlist.endpoints.dto.wishes.WishLinkDTO;
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.helpers.PageParams;
import de.marsg.wishlist.jpa.entities.ClaimEntity;
import de.marsg.wishlist.jpa.entities.UserTier;
import de.marsg.wishlist.jpa.entities.WishEntity;
import de.marsg.wishlist.jpa.entities.WishLinkEntity;
import de.marsg.wishlist.jpa.entities.WishlistEntity;
import de.marsg.wishlist.jpa.repositories.ClaimParticipantRepository;
import de.marsg.wishlist.jpa.repositories.ClaimRepository;
import de.marsg.wishlist.jpa.repositories.WishRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WishService {

    private static final Logger log = LoggerFactory.getLogger(WishService.class);

    private final WishRepository wishRepository;
    private final ClaimRepository claimRepository;
    private final ClaimParticipantRepository participantRepository;
    private final WishlistService wishlistService;
    private final AccessService accessService;
    private final LimitService limitService;

    public WishService(WishRepository wishRepository, ClaimRepository claimRepository,
            ClaimParticipantRepository participantRepository, WishlistService wishlistService,
            AccessService accessService, LimitService limitService) {
        this.wishRepository = wishRepository;
        this.claimRepository = claimRepository;
        this.participantRepository = participantRepository;
        this.wishlistService = wishlistService;
        this.accessService = accessService;
        this.limitService = limitService;
    }

    /** Owner gets the claim-free view; group members get the shared view with claims. */
    @Transactional(readOnly = true)
    public ReturnListDTO<?> listByWishlist(UUID userId, UUID wishlistId, Integer limit, Integer offset) {
        WishlistEntity wishlist = wishlistService.requireWishlist(wishlistId);
        Pageable pageable = PageParams.of(limit, offset, Sort.by(Sort.Direction.ASC, "id"));
        Page<WishEntity> wishes = wishRepository.findByWishlistsId(wishlistId, pageable);
        if (accessService.isWishlistOwner(userId, wishlist)) {
            return ReturnListDTO.of(wishes, ReturnOwnWishDTO::from);
        }
        accessService.assertCanViewWishlist(userId, wishlist);
        Map<UUID, List<ReturnWishClaimDTO>> claimsByWish =
                claimsByWish(wishes.getContent().stream().map(WishEntity::getId).toList(), userId);
        return ReturnListDTO.of(wishes, wishes.getContent().stream()
                .map(w -> ReturnSharedWishDTO.from(w, claimsByWish.getOrDefault(w.getId(), List.of())))
                .toList());
    }

    @Transactional(readOnly = true)
    public Object get(UUID userId, UUID wishId) {
        WishEntity wish = requireWish(wishId);
        WishlistEntity wishlist = wishlistService.requireWishlist(wish.getWishlistsId());
        if (accessService.isWishlistOwner(userId, wishlist)) {
            return ReturnOwnWishDTO.from(wish);
        }
        accessService.assertCanViewWishlist(userId, wishlist);
        return ReturnSharedWishDTO.from(wish,
                claimsByWish(List.of(wishId), userId).getOrDefault(wishId, List.of()));
    }

    @Transactional
    public ReturnOwnWishDTO create(UUID userId, UserTier tier, CreateWishDTO dto) {
        WishlistEntity wishlist = wishlistService.requireWishlist(dto.wishlistId());
        accessService.assertWishlistOwner(userId, wishlist);
        limitService.assertCanAddWish(wishlist.getId(), tier);
        WishEntity wish = new WishEntity(wishlist.getId(), dto.name(), dto.description(), dto.quantity());
        wish.replaceLinks(toLinkEntities(dto.links()));
        wish = wishRepository.save(wish);
        log.info("User {} created wish {} in wishlist {}", userId, wish.getId(), wishlist.getId());
        return ReturnOwnWishDTO.from(wish);
    }

    /** Quantity reductions below the claim count are allowed; existing claims are grandfathered. */
    @Transactional
    public ReturnUpdatedWishDTO update(UUID userId, UUID wishId, UpdateWishDTO dto) {
        WishEntity wish = requireWish(wishId);
        accessService.assertWishlistOwner(userId, wishlistService.requireWishlist(wish.getWishlistsId()));
        long claimCount = claimRepository.countByWishesId(wishId);
        boolean claimsAffected = dto.quantity() != null && claimCount > dto.quantity();
        wish.setName(dto.name());
        wish.setDescription(dto.description());
        wish.setQuantity(dto.quantity());
        wish.replaceLinks(toLinkEntities(dto.links()));
        return ReturnUpdatedWishDTO.from(wish, claimsAffected);
    }

    /** Owner learns only THAT claims exist — deleting then needs {@code confirm=true}. */
    @Transactional
    public void softDelete(UUID userId, UUID wishId, boolean confirm) {
        WishEntity wish = requireWish(wishId);
        accessService.assertWishlistOwner(userId, wishlistService.requireWishlist(wish.getWishlistsId()));
        if (!confirm && claimRepository.countByWishesId(wishId) > 0) {
            throw new ApiException(ErrorCode.WISH_HAS_CLAIMS);
        }
        wish.markDeleted();
        log.info("User {} soft-deleted wish {}", userId, wishId);
    }

    WishEntity requireWish(UUID wishId) {
        return wishRepository.findById(wishId)
                .orElseThrow(() -> new ApiException(ErrorCode.WISH_NOT_FOUND));
    }

    /**
     * Claims incl. participant displaynames, grouped per wish. The requester's own
     * participation is flagged here so clients never scan the participant list.
     */
    Map<UUID, List<ReturnWishClaimDTO>> claimsByWish(Collection<UUID> wishIds, UUID requesterId) {
        if (wishIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<ReturnSimpleUserDTO>> participantsByClaim = new LinkedHashMap<>();
        Set<UUID> claimsOfRequester = new HashSet<>();
        Map<UUID, Boolean> mayIdentify = new HashMap<>();
        for (Object[] row : participantRepository.findParticipantRowsForWishes(wishIds)) {
            UUID claimId = (UUID) row[0];
            UUID participantId = (UUID) row[2];
            // the wishlist may be shared into several groups, so a co-claimer is not
            // automatically someone this requester is allowed to put a name to
            boolean named = mayIdentify.computeIfAbsent(participantId,
                    id -> accessService.mayIdentify(requesterId, id));
            participantsByClaim.computeIfAbsent(claimId, k -> new ArrayList<>())
                    .add(named
                            ? new ReturnSimpleUserDTO(participantId, (String) row[3])
                            : ReturnSimpleUserDTO.unnamed(participantId));
            if (participantId.equals(requesterId)) {
                claimsOfRequester.add(claimId);
            }
        }
        Map<UUID, List<ReturnWishClaimDTO>> result = new LinkedHashMap<>();
        for (ClaimEntity claim : claimRepository.findByWishesIdIn(wishIds)) {
            // canJoin needs the sibling claims of the wish, so ReturnSharedWishDTO fills it in
            result.computeIfAbsent(claim.getWishesId(), k -> new ArrayList<>())
                    .add(new ReturnWishClaimDTO(claim.getId(),
                            participantsByClaim.getOrDefault(claim.getId(), List.of()),
                            claimsOfRequester.contains(claim.getId()),
                            requesterId.equals(claim.getUsersId()),
                            claim.isShared(),
                            false));
        }
        return result;
    }

    private List<WishLinkEntity> toLinkEntities(List<WishLinkDTO> links) {
        if (links == null) {
            return List.of();
        }
        List<WishLinkEntity> entities = new ArrayList<>(links.size());
        for (int i = 0; i < links.size(); i++) {
            entities.add(new WishLinkEntity(links.get(i).url(), links.get(i).label(), (short) i));
        }
        return entities;
    }
}
