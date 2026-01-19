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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.marsg.wishlist.wishlist.request.dto.wishes.UpdateWishClaimDTO;
import de.marsg.wishlist.wishlist.request.handler.JwtToUserDto;
import de.marsg.wishlist.wishlist.services.WishClaimingService;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/wishes/claims")
public class WishClaimsController {

    private final JwtToUserDto jwtToDto;
    private final WishClaimingService claimingService;

    public WishClaimsController(JwtToUserDto jwtToDto, WishClaimingService claimingService) {
        this.jwtToDto = jwtToDto;
        this.claimingService = claimingService;
    }

    @PostMapping("/{wish_id}")
    public ResponseEntity<Object> claimWish(@PathVariable("wish_id") UUID wishId,
            @AuthenticationPrincipal Jwt jwt) {

        return claimingService.create(wishId, jwtToDto.convert(jwt));
    }

    @PutMapping("/{wish_id}")
    public ResponseEntity<Object> updateBoughtState(@PathVariable("wish_id") UUID wishId,
            @Valid @RequestBody UpdateWishClaimDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        return claimingService.update(wishId, dto, jwtToDto.convert(jwt));
    }

    @DeleteMapping("/{wish_id}")
    public ResponseEntity<Object> removeClaim(@PathVariable("wish_id") UUID wishId,
            @AuthenticationPrincipal Jwt jwt) {
        return claimingService.delete(wishId, jwtToDto.convert(jwt));
    }

    @GetMapping()
    public ResponseEntity<Object> listClaimedWishes(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "offset", required = false, defaultValue = "0") int offset,
            @RequestParam(name = "bought", required = false) Boolean bought) {

        // Simple validation
        if (offset < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Offset must be >= 0", "errId", "INVALID_OFFSET"));
        }

        if (bought == null) {
            return claimingService.getClaimed(jwtToDto.convert(jwt), offset);
        } else {
            return claimingService.getClaimed(jwtToDto.convert(jwt), offset, bought);
        }

    }
}
