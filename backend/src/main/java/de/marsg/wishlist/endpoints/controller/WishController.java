package de.marsg.wishlist.endpoints.controller;

import de.marsg.wishlist.configs.security.AuthenticatedUser;
import de.marsg.wishlist.endpoints.dto.common.ReturnListDTO;
import de.marsg.wishlist.endpoints.dto.wishes.CreateWishDTO;
import de.marsg.wishlist.endpoints.dto.wishes.ReturnOwnWishDTO;
import de.marsg.wishlist.endpoints.dto.wishes.ReturnUpdatedWishDTO;
import de.marsg.wishlist.endpoints.dto.wishes.UpdateWishDTO;
import de.marsg.wishlist.endpoints.services.WishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/wishes")
@Tag(name = "wishes")
public class WishController {

    private final WishService wishService;

    public WishController(WishService wishService) {
        this.wishService = wishService;
    }

    @GetMapping
    @Operation(summary = "Wishes of a wishlist; owners get the claim-free view, "
            + "group members the shared view with claims (limit is clamped to 1..100)")
    public ReturnListDTO<?> list(AuthenticatedUser user, @RequestParam("wishlist_id") UUID wishlistId,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", required = false) Integer offset) {
        return wishService.listByWishlist(user.userId(), wishlistId, limit, offset);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a wish with links (wishlist owner, limit-checked)")
    public ReturnOwnWishDTO create(AuthenticatedUser user, @Valid @RequestBody CreateWishDTO body) {
        return wishService.create(user.userId(), user.tier(), body);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One wish; view depends on requester (owner vs group member)")
    public Object get(AuthenticatedUser user, @PathVariable("id") UUID id) {
        return wishService.get(user.userId(), id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a wish; links are replaced as a set. quantity is null (unlimited) "
            + "or 1. Switching unlimited -> 1 grandfathers existing claims and sets "
            + "existing_claims_affected")
    public ReturnUpdatedWishDTO update(AuthenticatedUser user, @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateWishDTO body) {
        return wishService.update(user.userId(), id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a wish; returns WISH_HAS_CLAIMS unless confirm=true when claims exist")
    public void delete(AuthenticatedUser user, @PathVariable("id") UUID id,
            @RequestParam(value = "confirm", defaultValue = "false") boolean confirm) {
        wishService.softDelete(user.userId(), id, confirm);
    }
}
