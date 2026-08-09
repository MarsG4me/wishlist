package de.marsg.wishlist.endpoints.controller;

import de.marsg.wishlist.configs.security.AuthenticatedUser;
import de.marsg.wishlist.endpoints.dto.users.ReturnOwnUserDTO;
import de.marsg.wishlist.endpoints.dto.users.ReturnSimpleUserDTO;
import de.marsg.wishlist.endpoints.dto.users.UpdateEmailDTO;
import de.marsg.wishlist.endpoints.dto.users.UpdatePasswordDTO;
import de.marsg.wishlist.endpoints.dto.users.UpdateTierDTO;
import de.marsg.wishlist.endpoints.dto.users.UpdateUserDTO;
import de.marsg.wishlist.endpoints.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
@Tag(name = "users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Own user; first call provisions the account from the JWT")
    public ReturnOwnUserDTO getMe(AuthenticatedUser user) {
        return userService.getOwnUser(user.userId());
    }

    @PatchMapping("/me")
    @Operation(summary = "Update the app-specific displayname")
    public ReturnOwnUserDTO updateMe(AuthenticatedUser user, @Valid @RequestBody UpdateUserDTO body) {
        return userService.updateDisplayname(user.userId(), body.displayname());
    }

    @PatchMapping("/me/email")
    @Operation(summary = "Change email (= username): applied in Keycloak first, then stored")
    public ReturnOwnUserDTO updateEmail(AuthenticatedUser user, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateEmailDTO body) {
        return userService.updateEmail(user.userId(), jwt.getTokenValue(), body.email());
    }

    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change password in-app: current password verified, then reset in Keycloak")
    public void updatePassword(AuthenticatedUser user, @Valid @RequestBody UpdatePasswordDTO body) {
        userService.updatePassword(user.userId(), body.currentPassword(), body.newPassword());
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete own account (restorable by logging in within 30 days)")
    public void deleteMe(AuthenticatedUser user) {
        userService.softDeleteOwnUser(user.userId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Another user's public info; requires a common group")
    public ReturnSimpleUserDTO getUser(AuthenticatedUser user, @PathVariable("id") UUID id) {
        return userService.getOtherUser(user.userId(), id);
    }

    @PatchMapping("/{id}/tier")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "Set a user's tier (admin only)")
    public ReturnOwnUserDTO setTier(@PathVariable("id") UUID id, @Valid @RequestBody UpdateTierDTO body) {
        return userService.setTier(id, body.tier());
    }
}
