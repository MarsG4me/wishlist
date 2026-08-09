package de.marsg.wishlist.endpoints.services;

import de.marsg.wishlist.endpoints.dto.claims.ReturnMyClaimDTO;
import de.marsg.wishlist.endpoints.dto.users.ReturnSimpleUserDTO;
import de.marsg.wishlist.endpoints.dto.wishes.ReturnWishClaimDTO;
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import de.marsg.wishlist.jpa.entities.ClaimEntity;
import de.marsg.wishlist.jpa.entities.ClaimParticipantEntity;
import de.marsg.wishlist.jpa.entities.UserEntity;
import de.marsg.wishlist.jpa.entities.WishEntity;
import de.marsg.wishlist.jpa.entities.WishlistEntity;
import de.marsg.wishlist.jpa.repositories.ClaimParticipantRepository;
import de.marsg.wishlist.jpa.repositories.ClaimRepository;
import de.marsg.wishlist.jpa.repositories.UserRepository;
import de.marsg.wishlist.jpa.repositories.WishRepository;
import de.marsg.wishlist.jpa.repositories.WishlistRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        return new ReturnWishClaimDTO(claim.getId(), participantNames(claim.getId()));
    }

    /** Joining an existing claim (money pooling) does not consume a wish unit. */
    @Transactional
    public ReturnWishClaimDTO join(UUID userId, UUID claimId) {
        ClaimEntity claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ApiException(ErrorCode.CLAIM_NOT_FOUND));
        lockedClaimableWish(userId, claim.getWishesId());
        participantRepository.save(new ClaimParticipantEntity(claimId, userId, claim.getWishesId()));
        log.info("User {} joined claim {}", userId, claimId);
        return new ReturnWishClaimDTO(claimId, participantNames(claimId));
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
        if (participantRepository.countByIdClaimsId(claimId) == 0) {
            claimRepository.deleteById(claimId);
            log.info("User {} left claim {}; claim removed (last participant)", userId, claimId);
        } else {
            log.info("User {} left claim {}", userId, claimId);
        }
    }

    @Transactional(readOnly = true)
    public List<ReturnMyClaimDTO> listMine(UUID userId) {
        List<ClaimParticipantEntity> participations = participantRepository.findByIdUsersId(userId);
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

        return participations.stream()
                .map(participation -> {
                    ClaimEntity claim = claims.get(participation.getClaimsId());
                    WishEntity wish = claim == null ? null : wishes.get(claim.getWishesId());
                    // claims on soft-deleted wishes/lists drop out of the overview
                    WishlistEntity wishlist = wish == null ? null : wishlists.get(wish.getWishlistsId());
                    if (wishlist == null) {
                        return null;
                    }
                    return new ReturnMyClaimDTO(claim.getId(), wish.getId(), wish.getName(),
                            wishlist.getId(), wishlist.getName(), owners.get(wishlist.getUsersId()),
                            participantNames(claim.getId()));
                })
                .filter(dto -> dto != null)
                .toList();
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

    private List<ReturnSimpleUserDTO> participantNames(UUID claimId) {
        List<UUID> userIds = participantRepository.findByIdClaimsId(claimId).stream()
                .map(ClaimParticipantEntity::getUsersId)
                .toList();
        return userRepository.findAllById(userIds).stream()
                .map(ReturnSimpleUserDTO::from)
                .toList();
    }
}
