package de.marsg.wishlist.wishlist.request;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.marsg.wishlist.wishlist.request.dto.wishes.CreateUpdateWishDTO;
import de.marsg.wishlist.wishlist.request.handler.JwtToUserDto;
import de.marsg.wishlist.wishlist.services.WishService;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/wishes")
public class WishController {

    private final JwtToUserDto jwtToDto;
    private final WishService wishService;

    public WishController(JwtToUserDto jwtToDto, WishService wishService) {
        this.jwtToDto = jwtToDto;
        this.wishService = wishService;
    }

    @PostMapping
    public ResponseEntity<Object> createWish(
            @Valid @RequestBody CreateUpdateWishDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        return wishService.create(dto, jwtToDto.convert(jwt));
    }

    @PutMapping("/{wish_id}")
    public ResponseEntity<Object> updateWish(
            @Valid @RequestBody CreateUpdateWishDTO dto,
            @PathVariable("wish_id") UUID wishId, @AuthenticationPrincipal Jwt jwt) {

        return wishService.edit(dto, wishId, jwtToDto.convert(jwt));
    }

    @DeleteMapping("/{wish_id}")
    public ResponseEntity<Object> deleteWish(@PathVariable("wish_id") UUID wishId,
            @AuthenticationPrincipal Jwt jwt) {

        return wishService.delete(wishId, jwtToDto.convert(jwt));
    }
}
