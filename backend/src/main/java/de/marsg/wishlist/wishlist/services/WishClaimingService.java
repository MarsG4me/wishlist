package de.marsg.wishlist.wishlist.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import de.marsg.wishlist.wishlist.data.jpa.entity.Wish;
import de.marsg.wishlist.wishlist.data.jpa.repository.WishRepository;
import de.marsg.wishlist.wishlist.data.jpa.repository.WishlistSharingRepository;
import de.marsg.wishlist.wishlist.logging.LogMgr;
import de.marsg.wishlist.wishlist.request.dto.users.UserDTO;
import de.marsg.wishlist.wishlist.request.dto.wishes.ReturnClaimedWishDTO;
import de.marsg.wishlist.wishlist.request.dto.wishes.ReturnClaimedWishesDTO;
import de.marsg.wishlist.wishlist.request.dto.wishes.UpdateWishClaimDTO;
import jakarta.transaction.Transactional;

@Service
public class WishClaimingService {

    private final LogMgr log;
    private final WishRepository wishRepository;
    private final WishService wishService;
    private final WishlistSharingRepository wishlistSharingRepository;
    private final UserService userService;

    public WishClaimingService(LogMgr log, WishRepository wishRepository,
            WishService wishService,
            WishlistSharingRepository wishlistSharingRepository,
            UserService userService) {
        this.log = log;
        this.wishRepository = wishRepository;
        this.wishService = wishService;
        this.wishlistSharingRepository = wishlistSharingRepository;
        this.userService = userService;
    }

    @Transactional
    public ResponseEntity<Object> create(UUID wishId, UserDTO userDto) {

        if (!userService.ensuredUserExistance(userDto)) {
            // Return user verification error
            return returnFailedUserVerification();
        }

        Optional<Wish> wish = wishRepository.findById(wishId);
        if (wish.isEmpty()) {
            log.logInfo("User %s tried to claim a wish that does not exist.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "The wish does not exist.",
                            "errId", "WISH_NOT_FOUND"));
        }

        if (wishService.isWishOwner(wish.get(), userDto)) {
            log.logInfo("User %s tried to claim their own wish.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    Map.of("error", "Not possible for yourself.",
                            "errId", "CANT_DO_SELF"));
        }

        if (!wishService.canWishBeClaimed(wishId)) {
            log.logInfo("User %s tried to claim a claimed wish.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("error", "This wish is already claimed.",
                            "errId", "ALREADY_CLAIMED"));
        }

        wish.get().setClaimer(userService.getUser(userDto));

        wishRepository.save(wish.get());

        log.logInfo("User %s claimed wish %s.", userDto.getFirstName(), wish.get().getName());

        return ResponseEntity.ok().body(Map.of());

    }

    @Transactional
    public ResponseEntity<Object> update(UUID wishId, UpdateWishClaimDTO dto, UserDTO userDto) {

        if (!userService.ensuredUserExistance(userDto)) {
            // Return user verification error
            return returnFailedUserVerification();
        }

        Optional<Wish> wish = wishRepository.findById(wishId);
        if (wish.isEmpty()) {
            log.logInfo("User %s tried to update the claim for a wish that does not exist.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "The wish does not exist.",
                            "errId", "WISH_NOT_FOUND"));
        }

        if (!wish.get().getClaimer().getKeycloakId().equals(userDto.getId())) {
            log.logInfo("User %s tried to update the claim for wish they didn't claim.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("error", "This wish is claimed by someone else.",
                            "errId", "NOT_YOUR_CLAIM"));
        }

        wish.get().setIsBoughtByClaimer(dto.bought());

        wishRepository.save(wish.get());

        log.logInfo("User %s claimed wish %s.", userDto.getFirstName(), wish.get().getName());

        return ResponseEntity.ok().body(Map.of());

    }

    @Transactional
    public ResponseEntity<Object> delete(UUID wishId, UserDTO userDto) {

        if (!userService.ensuredUserExistance(userDto)) {
            // Return user verification error
            return returnFailedUserVerification();
        }

        Optional<Wish> wish = wishRepository.findById(wishId);
        if (wish.isEmpty()) {
            log.logInfo("User %s tried to remove claim for wish that does not exist.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "The wish does not exist.",
                            "errId", "WISH_NOT_FOUND"));
        }

        if (!wish.get().getClaimer().getKeycloakId().equals(userDto.getId())) {
            log.logInfo("User %s tried to update the claim for wish they didn't claim.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("error", "This wish is claimed by someone else.",
                            "errId", "NOT_YOUR_CLAIM"));
        }

        wish.get().setClaimer(null);
        wish.get().setIsBoughtByClaimer(false);

        wishRepository.save(wish.get());

        log.logInfo("User %s removed claim for wish %s.", userDto.getFirstName(), wish.get().getName());

        return ResponseEntity.ok().body(Map.of());

    }

    public ResponseEntity<Object> getClaimed(UserDTO userDto, int offset) {

        if (!userService.ensuredUserExistance(userDto)) {
            // Return user verification error
            return returnFailedUserVerification();
        }

        List<Wish> claimedWishes = wishRepository.findAllByClaimerId(userDto.getId(), offset);

        long totalClaims = wishRepository.countAllByClaimerId(userDto.getId());

        return ResponseEntity.ok().body(
                new ReturnClaimedWishesDTO(totalClaims, offset, claimedWishes.size(),
                        claimedWishes.stream().map(wish -> new ReturnClaimedWishDTO(
                                wish.getId(),
                                wish.getName(),
                                wish.getDescription(),
                                wish.isClaimed(),
                                wish.isBoughtByClaimer())).toList()));

    }

    public ResponseEntity<Object> getClaimed(UserDTO userDto, int offset, boolean bought) {

        if (!userService.ensuredUserExistance(userDto)) {
            // Return user verification error
            return returnFailedUserVerification();
        }

        List<Wish> claimedWishes = wishRepository.findAllByIsBoughtAndClaimer(bought, userDto.getId(), offset);

        long totalClaims = wishRepository.countAllByIsBoughtAndClaimer(bought, userDto.getId());

        return ResponseEntity.ok().body(
                new ReturnClaimedWishesDTO(totalClaims, offset, claimedWishes.size(),
                        claimedWishes.stream().map(wish -> new ReturnClaimedWishDTO(
                                wish.getId(),
                                wish.getName(),
                                wish.getDescription(),
                                wish.isClaimed(),
                                wish.isBoughtByClaimer())).toList()));

    }

    /*
     * Helper functions
     */

    private ResponseEntity<Object> returnFailedUserVerification() {
        return ResponseEntity.internalServerError().body(
                Map.of("error", "User verification failed.",
                        "errId", "USER_VERIFICATION_FAILED"));
    }
}
