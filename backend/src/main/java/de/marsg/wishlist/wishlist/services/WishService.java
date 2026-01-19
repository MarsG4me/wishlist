package de.marsg.wishlist.wishlist.services;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.marsg.wishlist.wishlist.data.jpa.entity.User;
import de.marsg.wishlist.wishlist.data.jpa.entity.Wish;
import de.marsg.wishlist.wishlist.data.jpa.entity.Wishlist;
import de.marsg.wishlist.wishlist.data.jpa.repository.WishRepository;
import de.marsg.wishlist.wishlist.data.jpa.repository.WishlistRepository;
import de.marsg.wishlist.wishlist.data.jpa.repository.WishlistSharingRepository;
import de.marsg.wishlist.wishlist.logging.LogMgr;
import de.marsg.wishlist.wishlist.request.dto.users.UserDTO;
import de.marsg.wishlist.wishlist.request.dto.wishes.CreateUpdateWishDTO;
import de.marsg.wishlist.wishlist.request.dto.wishes.ReturnPublicWishDTO;
import jakarta.transaction.Transactional;

public class WishService {

    private static final long MAX_WISHES_PER_LIST = 50;

    private final LogMgr log;
    private final WishRepository wishRepository;
    private final WishlistRepository wishlistRepository;
    private final WishlistSharingRepository wishlistSharingRepository;
    private final UserService userService;

    public WishService(LogMgr log, WishRepository wishRepository,
            WishlistRepository wishlistRepository,
            WishlistSharingRepository wishlistSharingRepository,
            UserService userService) {
        this.log = log;
        this.wishRepository = wishRepository;
        this.wishlistRepository = wishlistRepository;
        this.wishlistSharingRepository = wishlistSharingRepository;
        this.userService = userService;
    }

    @Transactional
    public ResponseEntity<Object> create(CreateUpdateWishDTO dto, UserDTO userDto) {

        if (!userService.ensuredUserExistance(userDto)) {
            // Return user verification error
            return returnFailedUserVerification();
        }

        Optional<Wishlist> wishlist = wishlistRepository.findById(dto.wishlist_id());
        if (wishlist.isEmpty() || !wishlist.get().getOwner().getKeycloakId().equals(userDto.getId())) {
            log.logInfo("User %s tried to create a wish in a wishlist but is not allowed to.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("error", "No permission to access wishlist.",
                            "errId", "NOT_PERMITTED_WISHLIST"));
        }

        if (countWishesInWishlist(wishlist.get()) >= MAX_WISHES_PER_LIST) {
            log.logWarning("User %s tried to create a 51. wish in a wishlist.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("error", "User can't create more wishes in this wishlist.",
                            "errId", "USER_REACHED_WISH_CREATION_LIMIT"));
        }

        Wish wish = new Wish(dto.name(), wishlist.get());
        wish.setDescription(dto.description());
        wish.setUnlimited(dto.unlimited());

        wish = wishRepository.save(wish);

        var returnDto = new ReturnPublicWishDTO(
                wish.getId(), wish.getName(), wish.getDescription(), wish.isUnlimited(), wish.isClaimed());

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest() // captures /api/wishlists
                .path("/{id}") // append the new resource ID
                .buildAndExpand(wish.getId())
                .toUri();

        return ResponseEntity.created(location).body(returnDto);
    }

    @Transactional
    public ResponseEntity<Object> edit(CreateUpdateWishDTO dto, UUID wishId, UserDTO userDto) {

        if (!userService.ensuredUserExistance(userDto)) {
            // Return user verification error
            return returnFailedUserVerification();
        }

        Optional<Wish> wish = wishRepository.findById(wishId);
        if (wish.isEmpty()) {
            log.logInfo("User %s tried to edit a wish that does not exist.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "The wish does not exist.",
                            "errId", "WISH_NOT_FOUND"));
        }

        if (!isWishOwner(wish.get(), userDto)) {
            log.logInfo("User %s tried to edit a wish but is not the owner.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("error", "Can only be done by wish owner.",
                            "errId", "NOT_THE_WISH_OWNER"));
        }

        wish.get().setName(dto.name());
        wish.get().setDescription(dto.description());
        wish.get().setUnlimited(dto.unlimited());

        //Do the wishlist check again if the wishlist was changed
        if (!wish.get().getWishlist().getId().equals(dto.wishlist_id())) {
            Optional<Wishlist> wishlist = wishlistRepository.findById(dto.wishlist_id());
            //Ensure on wishlist update that the new list is owned by the user and can accept more wishes
            if (wishlist.isEmpty() || !wishlist.get().getOwner().getKeycloakId().equals(userDto.getId())) {
                log.logInfo("User %s tried to create a wish in a wishlist but is not allowed to.",
                        userDto.getFirstName());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                        Map.of("error", "No permission to access wishlist.",
                                "errId", "NOT_PERMITTED_WISHLIST"));
            }

            if (countWishesInWishlist(wishlist.get()) >= MAX_WISHES_PER_LIST) {
                log.logWarning("User %s tried to create a 51. wish in a wishlist.", userDto.getFirstName());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                        Map.of("error", "User can't create more wishes in this wishlist.",
                                "errId", "USER_REACHED_WISH_CREATION_LIMIT"));
            }

            wish.get().setWishlist(wishlist.get());
        }

        wishRepository.save(wish.get());

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
            log.logInfo("User %s tried to delete a wish that does not exist.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "The wish does not exist.",
                            "errId", "WISH_NOT_FOUND"));
        }

        if (!isWishOwner(wish.get(), userDto)) {
            log.logInfo("User %s tried to delete a wish but is not the owner.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("error", "Can only be done by wish owner.",
                            "errId", "NOT_THE_WISH_OWNER"));
        }

        wishRepository.delete(wish.get());

        return ResponseEntity.ok().body(Map.of());
    }

    /*
     * Helper functions
     */

    public boolean canUserSeeWish(UUID userId, UUID wishId) {
        Optional<Wish> wish = wishRepository.findById(wishId);
        if (wish.isEmpty()) {
            return false;
        }

        return wishlistSharingRepository.isUserInSharedWishlistGroup(wish.get().getWishlist().getId(), userId)
                || wishlistSharingRepository.isUserOwnerOfWishlist(wish.get().getWishlist().getId(), userId);
    }

    public boolean isWishOwner(Wish wish, UserDTO userDto) {
        return wish.getWishlist().getOwner().getKeycloakId().equals(userDto.getId());
    }

    public boolean canWishBeClaimed(UUID wishId) {
        Optional<Wish> wish = wishRepository.findById(wishId);
        if (wish.isEmpty()) {
            return false;
        }

        return !wish.get().isClaimed() || wish.get().isUnlimited();
    }

    public boolean isWishClaimed(UUID wishId) {
        Optional<Wish> wish = wishRepository.findById(wishId);
        if (wish.isEmpty()) {
            return false;
        }
        return wish.get().isClaimed();
    }

    public User getClaimerOfWish(UUID wishId) {
        Optional<Wish> wish = wishRepository.findById(wishId);
        if (wish.isEmpty()) {
            return null;
        }
        return wish.get().getClaimer();
    }

    public long countWishesInWishlist(Wishlist wishlist) {
        return wishRepository.countAllByWishlist(wishlist);
    }

    public List<Wish> getAllWishesInWishlist(Wishlist wishlist) {
        return wishRepository.findAllByWishlist(wishlist);
    }

    public boolean canDelete(Wish wish) {
        return !wish.isClaimed();
    }

    public boolean delete(Wish wish) {
        if (canDelete(wish)) {
            wishRepository.delete(wish);
            return true;
        }
        return false;
    }

    private ResponseEntity<Object> returnFailedUserVerification() {
        return ResponseEntity.internalServerError().body(
                Map.of("error", "User verification failed.",
                        "errId", "USER_VERIFICATION_FAILED"));
    }
}
