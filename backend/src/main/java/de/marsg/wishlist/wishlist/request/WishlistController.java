package de.marsg.wishlist.wishlist.request;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.marsg.wishlist.wishlist.request.dto.wishlists.CreateUpdateWishlistDTO;
import de.marsg.wishlist.wishlist.request.handler.JwtToUserDto;
import de.marsg.wishlist.wishlist.services.WishlistService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/wishlists")
public class WishlistController {

    private final JwtToUserDto jwtToDto;
    private final WishlistService wishlistService;

    public WishlistController(JwtToUserDto jwtToDto, WishlistService wishlistService) {
        this.jwtToDto = jwtToDto;
        this.wishlistService = wishlistService;
    }

    @PostMapping
    public ResponseEntity<Object> createWishlist(@Valid @RequestBody CreateUpdateWishlistDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        return wishlistService.create(jwtToDto.convert(jwt), dto);
    }

    @GetMapping
    public ResponseEntity<Object> getAllWishlistsOfUser(
            @RequestParam(name = "offset", required = false, defaultValue = "0") int offset,
            @AuthenticationPrincipal Jwt jwt) {

        // Simple validation
        if (offset < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Offset must be >= 0", "errId", "INVALID_OFFSET"));
        }

        return wishlistService.getAllWishlistsOfUser(jwtToDto.convert(jwt), offset);
    }

    @GetMapping("/{wishlist_id}")
    public ResponseEntity<Object> getWishlist(@PathVariable("wishlist_id") UUID wishlistId,
            @AuthenticationPrincipal Jwt jwt) {

        return wishlistService.getWishlist(wishlistId, jwtToDto.convert(jwt));
    }

    @PutMapping("/{wishlist_id}")
    public ResponseEntity<Object> updateWishlist(
            @Valid @RequestBody CreateUpdateWishlistDTO dto,
            @PathVariable("wishlist_id") UUID wishlistId, @AuthenticationPrincipal Jwt jwt) {

        return wishlistService.edit(wishlistId, jwtToDto.convert(jwt), dto);
    }

    @DeleteMapping("/{wishlist_id}")
    public ResponseEntity<Object> deleteWishlist(@PathVariable("wishlist_id") UUID wishlistId,
            @AuthenticationPrincipal Jwt jwt) {

        return wishlistService.delete(wishlistId, jwtToDto.convert(jwt));
    }
}
