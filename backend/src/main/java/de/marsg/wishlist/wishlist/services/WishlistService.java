package de.marsg.wishlist.wishlist.services;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.marsg.wishlist.wishlist.data.jpa.entity.User;
import de.marsg.wishlist.wishlist.data.jpa.entity.Wish;
import de.marsg.wishlist.wishlist.data.jpa.entity.Wishlist;
import de.marsg.wishlist.wishlist.data.jpa.repository.WishlistRepository;
import de.marsg.wishlist.wishlist.data.jpa.repository.WishlistSharingRepository;
import de.marsg.wishlist.wishlist.logging.LogMgr;
import de.marsg.wishlist.wishlist.request.dto.users.UserDTO;
import de.marsg.wishlist.wishlist.request.dto.wishlists.CreateUpdateWishlistDTO;
import de.marsg.wishlist.wishlist.request.dto.wishlists.ReturnWishlistDto;
import de.marsg.wishlist.wishlist.request.dto.wishlists.ReturnWishlistListDto;
import jakarta.transaction.Transactional;

@Service
public class WishlistService {

    private static final int MAX_WISHLISTS_PER_USER = 5;

    private final LogMgr log;
    private final WishlistRepository wishlistRepository;
    private final UserService userService;
    private final WishService wishService;
    private final WishlistSharingRepository wishlistSharingRepository;

    public WishlistService(LogMgr log, WishlistRepository wishlistRepository, UserService userService,
            WishService wishService, WishlistSharingRepository wishlistSharingRepository) {
        this.log = log;
        this.wishlistRepository = wishlistRepository;
        this.userService = userService;
        this.wishService = wishService;
        this.wishlistSharingRepository = wishlistSharingRepository;
    }

    public ResponseEntity<Object> create(UserDTO userDto, CreateUpdateWishlistDTO dto) {

        if (!userService.ensuredUserExistance(userDto)) {
            // Return user verification error
            return returnFailedUserVerification();
        }

        if (!canCreateMoreWishlists(userDto)) {
            log.logWarning("User %s tried to create a 6. wishlist.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("error", "User can't create more wishlists.",
                            "errId", "USER_REACHED_WISHLIST_CREATION_LIMIT"));
        }

        User user = userService.getUser(userDto);

        Wishlist wishlist = new Wishlist(user);
        wishlist.setName(dto.name());

        wishlist = wishlistRepository.save(wishlist);

        ReturnWishlistDto returnDto = new ReturnWishlistDto(wishlist.getId(), wishlist.getName(), 0,
                wishlist.getOwner().getKeycloakId());

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest() // captures /api/wishlists
                .path("/{id}") // append the new resource ID
                .buildAndExpand(wishlist.getId())
                .toUri();

        return ResponseEntity.created(location).body(returnDto);

    }

    public ResponseEntity<Object> getWishlist(UUID wishlistId, UserDTO userDto) {

        if (!userService.ensuredUserExistance(userDto)) {
            // Return user verification error
            return returnFailedUserVerification();
        }

        Optional<Wishlist> wishlist = wishlistRepository.findById(wishlistId);

        if (wishlist.isEmpty()) {
            log.logInfo("User %s tried to access a wishlist that does not exist.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "The wishlist does not exist.",
                            "errId", "WISHLIST_NOT_FOUND"));
        }

        if (!wishlistSharingRepository.isUserInSharedWishlistGroup(wishlistId, userDto.getId()) &&
                !wishlistSharingRepository.isUserOwnerOfWishlist(wishlistId, userDto.getId())) {
            log.logInfo("User %s tried to access a wishlist but is not allowed to.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of("error", "No permission to access wishlist.",
                            "errId", "NOT_PERMITTED_WISHLIST"));
        }

        return ResponseEntity.ok().body(
                new ReturnWishlistDto(
                        wishlistId, wishlist.get().getName(),
                        wishService.countWishesInWishlist(wishlist.get()), wishlist.get().getOwner().getKeycloakId()));
    }

    public ResponseEntity<Object> getAllWishlistsOfUser(UserDTO userDto, int offset) {

        if (!userService.ensuredUserExistance(userDto)) {
            // Return user verification error
            return returnFailedUserVerification();
        }

        List<ReturnWishlistDto> wishlistDtos = wishlistRepository.findAllByOwner(userDto.getId(), offset).stream()
                .map(w -> new ReturnWishlistDto(
                        w.getId(),
                        w.getName(),
                        wishService.countWishesInWishlist(w),
                        w.getOwner().getKeycloakId()))
                .toList();

        return ResponseEntity.ok()
                .body(new ReturnWishlistListDto(wishlistDtos.size(), offset, wishlistDtos.size(), wishlistDtos));
    }

    public ResponseEntity<Object> edit(UUID wishlistId, UserDTO userDto, CreateUpdateWishlistDTO dto) {

        if (!userService.ensuredUserExistance(userDto)) {
            // Return user verification error
            return returnFailedUserVerification();
        }

        Optional<Wishlist> wishlist = wishlistRepository.findById(wishlistId);

        if (wishlist.isEmpty()) {
            log.logInfo("User %s tried to edit a wishlist that does not exist.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "The wishlist does not exist.",
                            "errId", "WISHLIST_NOT_FOUND"));
        }

        if (!wishlist.get().getOwner().getKeycloakId().equals(userDto.getId())) {
            log.logInfo("User %s tried to edit a wishlist but is not the owner.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("error", "Can only be done by wishlist owner.",
                            "errId", "NOT_THE_WISHLIST_OWNER"));
        }

        wishlist.get().setName(dto.name());
        wishlistRepository.save(wishlist.get());

        return ResponseEntity.ok().body(Map.of());
    }

    @Transactional
    public ResponseEntity<Object> delete(UUID wishlistId, UserDTO userDto) {

        if (!userService.ensuredUserExistance(userDto)) {
            // Return user verification error
            return returnFailedUserVerification();
        }

        Optional<Wishlist> wishlist = wishlistRepository.findById(wishlistId);

        if (wishlist.isEmpty()) {
            log.logInfo("User %s tried to edit a wishlist that does not exist.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "The wishlist does not exist.",
                            "errId", "WISHLIST_NOT_FOUND"));
        }

        if (!wishlist.get().getOwner().getKeycloakId().equals(userDto.getId())) {
            log.logInfo("User %s tried to edit a wishlist but is not the owner.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("error", "Can only be done by wishlist owner.",
                            "errId", "NOT_THE_WISHLIST_OWNER"));
        }

        boolean deletionLock = false;

        for (Wish wish : wishService.getAllWishesInWishlist(wishlist.get())) {
            if (!wishService.delete(wish)) {
                deletionLock = true;
            }
        }

        if (deletionLock) {
            log.logInfo("User %s tried to delete a wishlist that has claimed wishes.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    Map.of("error", "Wishlist contains claimed wishes.",
                            "errId", "WISHLIST_CONTAINS_CLAIMED_WISHES"));
        }

        wishlistRepository.delete(wishlist.get());

        return ResponseEntity.ok().body(Map.of());

    }

    /*
     * Helper
     */

    private boolean canCreateMoreWishlists(UserDTO user) {
        return wishlistRepository.countByOwner(userService.getUser(user)) < MAX_WISHLISTS_PER_USER;
    }

    private ResponseEntity<Object> returnFailedUserVerification() {
        return ResponseEntity.internalServerError().body(
                Map.of("error", "User verification failed.",
                        "errId", "USER_VERIFICATION_FAILED"));
    }
}
