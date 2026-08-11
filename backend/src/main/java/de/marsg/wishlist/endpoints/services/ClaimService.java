package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.endpoints.dto.claims.ReturnMyClaimDTO;
import de.marsg.wishlist.endpoints.dto.common.ReturnListDTO;
import de.marsg.wishlist.endpoints.dto.users.ReturnSimpleUserDTO;
import de.marsg.wishlist.endpoints.dto.wishes.ReturnWishClaimDTO;
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.helpers.PageParams;
import de.marsg.wishlist.jpa.entities.ClaimEntity;
import de.marsg.wishlist.jpa.entities.ClaimParticipantEntity;
import de.marsg.wishlist.jpa.entities.ClaimParticipantId;
import de.marsg.wishlist.jpa.entities.UserEntity;
import de.marsg.wishlist.jpa.entities.WishEntity;
import de.marsg.wishlist.jpa.entities.WishlistEntity;
import de.marsg.wishlist.jpa.repositories.ClaimParticipantRepository;
import de.marsg.wishlist.jpa.repositories.ClaimRepository;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import de.marsg.wishlist.jpa.repositories.WishRepository;
import de.marsg.wishlist.jpa.repositories.WishlistRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claiming rules: one participation per user per wish (own claim OR co-claim); the
 * number of claims on a wish is capped by its quantity (unless unlimited); the wish
 * owner must never see any of this. Capacity checks run under a pessimistic lock on
 * the wish row so concurrent claims cannot oversell.
 */
@Service
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

    private final ClaimRepository claimRepository;
    private final ClaimParticipantRepository participantRepository;
    private final WishRepository wishRepository;
    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;
    private final AccessService accessService;

    public ClaimService(ClaimRepository claimRepository, ClaimParticipantRepository participantRepository,
            WishRepository wishRepository, WishlistRepository wishlistRepository,
            UserRepository userRepository, AccessService accessService) {
        this.claimRepository = claimRepository;
        this.participantRepository = participantRepository;
        this.wishRepository = wishRepository;
        this.wishlistRepository = wishlistRepository;
        this.userRepository = userRepository;
        this.accessService = accessService;
    }

    @Transactional
    public ReturnWishClaimDTO create(UUID userId, UUID wishId) {
        WishEntity wish = lockedClaimableWish(userId, wishId);
        if (wish.getQuantity() != null && claimRepository.countByWishesId(wishId) >= wish.getQuantity()) {
            throw new ApiException(ErrorCode.WISH_FULLY_CLAIMED);
        }
        ClaimEntity claim = claimRepository.save(new ClaimEntity(wishId, userId));
        participantRepository.save(new ClaimParticipantEntity(claim.getId(), userId, wishId));
        log.info("User {} claimed wish {} (claim {})", userId, wishId, claim.getId());
        // the requester just created it, so they are its participant and owner by definition
        return mine(userId, claim);
    }

    /**
     * Joining an existing claim (money pooling) does not consume a wish unit, but the
     * claim's creator decides whether that is allowed at all — the wish owner never does.
     */
    @Transactional
    public ReturnWishClaimDTO join(UUID userId, UUID claimId) {
        ClaimEntity claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ApiException(ErrorCode.CLAIM_NOT_FOUND));
        lockedClaimableWish(userId, claim.getWishesId());
        if (!claim.isShared()) {
            throw new ApiException(ErrorCode.CLAIM_NOT_SHARED);
        }
        participantRepository.save(new ClaimParticipantEntity(claimId, userId, claim.getWishesId()));
        log.info("User {} joined claim {}", userId, claimId);
        return mine(userId, claim);
    }

    /** Opening/closing a claim for co-claimers is the creator's call alone. */
    @Transactional
    public ReturnWishClaimDTO setShared(UUID userId, UUID claimId, boolean shared) {
        ClaimEntity claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ApiException(ErrorCode.CLAIM_NOT_FOUND));
        // being recorded as creator is not enough: someone who left the claim (or lost
        // access to the wishlist) must not keep steering who may pool money into it
        if (!userId.equals(claim.getUsersId())
                || !participantRepository.existsById(new ClaimParticipantId(claimId, userId))) {
            throw new ApiException(ErrorCode.NOT_CLAIM_OWNER);
        }
        claim.setShared(shared);
        log.info("User {} set claim {} shared={}", userId, claimId, shared);
        return mine(userId, claim);
    }

    /** Leaving as the last participant hard-deletes the claim, freeing the wish unit. */
    @Transactional
    public void leave(UUID userId, UUID claimId) {
        ClaimParticipantEntity participant = participantRepository.findByIdClaimsId(claimId).stream()
                .filter(row -> row.getUsersId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.CLAIM_NOT_FOUND));
        participantRepository.delete(participant);
        participantRepository.flush();
        log.info("User {} left claim {}", userId, claimId);
        settleClaimAfterDeparture(claimId);
    }

    /**
     * Revocation cleanup. A claim is only ever visible through the wishlist it hangs on,
     * so losing sight of that wishlist must end the participation too — otherwise an
     * unshared wishlist or a removed group member keeps leaking wish, wishlist and owner
     * through the claim overview. Call after the revoking change is flushed, with every
     * wishlist whose audience it touched.
     */
    @Transactional
    public void purgeParticipationsWithoutAccess(Collection<UUID> wishlistIds) {
        if (wishlistIds.isEmpty()) {
            return;
        }
        Map<UUID, UUID> wishlistByWish = wishRepository.findByWishlistsIdIn(wishlistIds).stream()
                .collect(Collectors.toMap(WishEntity::getId, WishEntity::getWishlistsId));
        if (wishlistByWish.isEmpty()) {
            return;
        }
        Map<Viewer, Boolean> mayView = new HashMap<>();
        List<ClaimParticipantEntity> stale =
                participantRepository.findByWishesIdIn(wishlistByWish.keySet()).stream()
                        .filter(participant -> !mayView.computeIfAbsent(
                                new Viewer(participant.getUsersId(),
                                        wishlistByWish.get(participant.getWishesId())),
                                viewer -> accessService.canViewAsSharedWishlist(
                                        viewer.userId(), viewer.wishlistId())))
                        .toList();
        if (stale.isEmpty()) {
            return;
        }
        participantRepository.deleteAll(stale);
        participantRepository.flush();
        stale.stream().map(ClaimParticipantEntity::getClaimsId).distinct()
                .forEach(this::settleClaimAfterDeparture);
        log.info("Revoked {} claim participation(s) after access to {} wishlist(s) changed",
                stale.size(), wishlistIds.size());
    }

    /**
     * Account deletion: the person is gone, so their participations go with them. Without
     * this a deleted account would keep a single-unit wish blocked by a claim that renders
     * with nobody in it, until the 30-day purge finally cascades it away.
     */
    @Transactional
    public void removeAllParticipationsOf(UUID userId) {
        List<ClaimParticipantEntity> participations = participantRepository.findByIdUsersId(userId);
        if (participations.isEmpty()) {
            return;
        }
        participantRepository.deleteAll(participations);
        participantRepository.flush();
        participations.stream().map(ClaimParticipantEntity::getClaimsId).distinct()
                .forEach(this::settleClaimAfterDeparture);
        log.info("Removed {} claim participation(s) of user {}", participations.size(), userId);
    }

    @Transactional(readOnly = true)
    public ReturnListDTO<ReturnMyClaimDTO> listMine(UUID userId, Integer limit, Integer offset) {
        Pageable pageable = PageParams.of(limit, offset, Sort.by(Sort.Direction.DESC, "id.claimsId"));
        Page<ClaimParticipantEntity> page = participantRepository.findByIdUsersId(userId, pageable);
        List<ClaimParticipantEntity> participations = page.getContent();
        List<UUID> claimIds = participations.stream().map(ClaimParticipantEntity::getClaimsId).toList();
        Map<UUID, ClaimEntity> claims = claimRepository.findAllById(claimIds).stream()
                .collect(Collectors.toMap(ClaimEntity::getId, claim -> claim));
        List<UUID> wishIds = claims.values().stream().map(ClaimEntity::getWishesId).distinct().toList();
        Map<UUID, WishEntity> wishes = wishRepository.findAllById(wishIds).stream()
                .collect(Collectors.toMap(WishEntity::getId, wish -> wish));
        Map<UUID, WishlistEntity> wishlists = wishlistRepository.findAllById(
                        wishes.values().stream().map(WishEntity::getWishlistsId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(WishlistEntity::getId, wishlist -> wishlist));
        Map<UUID, ReturnSimpleUserDTO> owners = userRepository.findAllById(
                        wishlists.values().stream().map(WishlistEntity::getUsersId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, ReturnSimpleUserDTO::from));

        Map<UUID, Boolean> mayView = new HashMap<>();
        return ReturnListDTO.of(page, participations.stream()
                .map(participation -> {
                    ClaimEntity claim = claims.get(participation.getClaimsId());
                    WishEntity wish = claim == null ? null : wishes.get(claim.getWishesId());
                    // claims on soft-deleted wishes/lists drop out of the overview
                    WishlistEntity wishlist = wish == null ? null : wishlists.get(wish.getWishlistsId());
                    if (wishlist == null) {
                        return null;
                    }
                    // second line of defence behind purgeParticipationsWithoutAccess: a claim
                    // must never outlive the share that made its wishlist visible
                    boolean visible = mayView.computeIfAbsent(wishlist.getId(),
                            id -> accessService.canViewAsSharedWishlist(userId, id));
                    if (!visible) {
                        return null;
                    }
                    return new ReturnMyClaimDTO(claim.getId(), wish.getId(), wish.getName(),
                            wishlist.getId(), wishlist.getName(), owners.get(wishlist.getUsersId()),
                            participantNames(userId, claim.getId()), userId.equals(claim.getUsersId()),
                            claim.isShared());
                })
                .filter(Objects::nonNull)
                .toList());
    }

    /**
     * Locks the wish row and verifies the requester may claim it: the wish must be
     * visible via a group share and must not be the requester's own.
     */
    private WishEntity lockedClaimableWish(UUID userId, UUID wishId) {
        WishEntity wish = wishRepository.findByIdForUpdate(wishId)
                .orElseThrow(() -> new ApiException(ErrorCode.WISH_NOT_FOUND));
        WishlistEntity wishlist = wishlistRepository.findById(wish.getWishlistsId())
                .orElseThrow(() -> new ApiException(ErrorCode.WISH_NOT_FOUND));
        if (wishlist.getUsersId().equals(userId)) {
            throw new ApiException(ErrorCode.CANNOT_CLAIM_OWN_WISH);
        }
        if (!accessService.canViewAsSharedWishlist(userId, wishlist.getId())) {
            // hide existence of wishes the requester has no access to
            throw new ApiException(ErrorCode.WISH_NOT_FOUND);
        }
        if (participantRepository.existsByWishesIdAndIdUsersId(wishId, userId)) {
            throw new ApiException(ErrorCode.ALREADY_CLAIMED);
        }
        return wish;
    }

    /**
     * A claim the requester just created, joined or reconfigured: they participate in it
     * by definition, so joining again is never an option here.
     */
    private ReturnWishClaimDTO mine(UUID userId, ClaimEntity claim) {
        return new ReturnWishClaimDTO(claim.getId(), participantNames(userId, claim.getId()), true,
                userId.equals(claim.getUsersId()), claim.isShared(), false);
    }

    /**
     * A claim without its creator is not abandoned as long as co-claimers keep paying into
     * it: the creator rights move to one of them. Only an empty claim is deleted, which
     * frees the wish unit again.
     */
    private void settleClaimAfterDeparture(UUID claimId) {
        List<ClaimParticipantEntity> remaining = participantRepository.findByIdClaimsId(claimId);
        if (remaining.isEmpty()) {
            claimRepository.deleteById(claimId);
            log.info("Claim {} removed (last participant gone)", claimId);
            return;
        }
        claimRepository.findById(claimId).ifPresent(claim -> {
            boolean creatorStillIn = remaining.stream()
                    .anyMatch(row -> row.getUsersId().equals(claim.getUsersId()));
            if (!creatorStillIn) {
                UUID heir = remaining.stream()
                        .map(ClaimParticipantEntity::getUsersId)
                        .min(Comparator.naturalOrder())
                        .orElseThrow();
                claim.transferCreatorTo(heir);
                log.info("Transferred claim {} to co-claimer {}", claimId, heir);
            }
        });
    }

    /** Co-claimers the requester shares no group with stay unnamed — see AccessService#mayIdentify. */
    private List<ReturnSimpleUserDTO> participantNames(UUID requesterId, UUID claimId) {
        List<UUID> userIds = participantRepository.findByIdClaimsId(claimId).stream()
                .map(ClaimParticipantEntity::getUsersId)
                .toList();
        return userRepository.findAllById(userIds).stream()
                .map(user -> accessService.mayIdentify(requesterId, user.getId())
                        ? ReturnSimpleUserDTO.from(user)
                        : ReturnSimpleUserDTO.unnamed(user.getId()))
                .toList();
    }

    /** Cache key of the "may this user still see that wishlist" question. */
    private record Viewer(UUID userId, UUID wishlistId) {
    }
}
