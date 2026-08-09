package de.marsg.wishlist.endpoints.controller;

import de.marsg.wishlist.configs.security.AuthenticatedUser;
import de.marsg.wishlist.endpoints.dto.wishlists.CreateUpdateWishlistDTO;
import de.marsg.wishlist.endpoints.dto.wishlists.ReturnWishlistDTO;
import de.marsg.wishlist.endpoints.services.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/wishlists")
@Tag(name = "wishlists")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @GetMapping
    @Operation(summary = "Own wishlists")
    public List<ReturnWishlistDTO> list(AuthenticatedUser user) {
        return wishlistService.listOwn(user.userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a wishlist (limit-checked)")
    public ReturnWishlistDTO create(AuthenticatedUser user, @Valid @RequestBody CreateUpdateWishlistDTO body) {
        return wishlistService.create(user.userId(), user.tier(), body.name());
    }

    @GetMapping("/{id}")
    @Operation(summary = "One wishlist; visible to owner and members of groups it is shared into")
    public ReturnWishlistDTO get(AuthenticatedUser user, @PathVariable("id") UUID id) {
        return wishlistService.get(user.userId(), id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename a wishlist (owner)")
    public ReturnWishlistDTO rename(AuthenticatedUser user, @PathVariable("id") UUID id,
            @Valid @RequestBody CreateUpdateWishlistDTO body) {
        return wishlistService.rename(user.userId(), id, body.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a wishlist (owner)")
    public void delete(AuthenticatedUser user, @PathVariable("id") UUID id) {
        wishlistService.softDelete(user.userId(), id);
    }
}
