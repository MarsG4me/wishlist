package de.marsg.wishlist.wishlist.request;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.marsg.wishlist.wishlist.request.handler.JwtToUserDto;
import de.marsg.wishlist.wishlist.services.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final JwtToUserDto jwtToUser;

    public UserController(UserService userService, JwtToUserDto jwtToUser){
        this.userService = userService;
        this.jwtToUser = jwtToUser;
    }

    @GetMapping
    public ResponseEntity<Object> getOwnUser(@AuthenticationPrincipal Jwt jwt){
        return userService.fetchOwnUserInfo(jwtToUser.convert(jwt));
    }

    @GetMapping("/{user_id}")
    public ResponseEntity<Object> getWishlist(@PathVariable("user_id") UUID userId,
            @AuthenticationPrincipal Jwt jwt) {

        return userService.fetchOtherUserInfo(userId, jwtToUser.convert(jwt));
    }
}
