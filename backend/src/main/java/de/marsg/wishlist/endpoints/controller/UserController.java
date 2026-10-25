package de.marsg.wishlist.endpoints.controller;

import de.marsg.wishlist.configs.security.AuthenticatedUser;
import de.marsg.wishlist.endpoints.dto.users.ReturnOwnUserDTO;
import de.marsg.wishlist.endpoints.dto.users.ReturnSimpleUserDTO;
import de.marsg.wishlist.endpoints.dto.users.UpdateEmailDTO;
import de.marsg.wishlist.endpoints.dto.users.UpdateLocaleDTO;
import de.marsg.wishlist.endpoints.dto.users.UpdatePasswordDTO;
import de.marsg.wishlist.endpoints.dto.users.UpdateTierDTO;
import de.marsg.wishlist.endpoints.dto.users.UpdateUserDTO;
import de.marsg.wishlist.endpoints.dto.users.ReturnUserExportDTO;
import de.marsg.wishlist.endpoints.services.UserExportService;
import de.marsg.wishlist.endpoints.services.UserService;
import de.marsg.wishlist.helpers.ApiException;
import de.marsg.wishlist.helpers.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final UserExportService exportService;

    public UserController(UserService userService, UserExportService exportService) {
        this.userService = userService;
        this.exportService = exportService;
    }

    @GetMapping("/me")
    @Operation(summary = "Own user; first call provisions the account from the JWT")
    public ReturnOwnUserDTO getMe(AuthenticatedUser user) {
        return userService.getOwnUser(user.userId(), user.identityProvider());
    }

    @PatchMapping("/me")
    @Operation(summary = "Update the app-specific displayname")
    public ReturnOwnUserDTO updateMe(AuthenticatedUser user, @Valid @RequestBody UpdateUserDTO body) {
        return userService.updateDisplayname(user.userId(), user.identityProvider(), body.displayname());
    }

    @PatchMapping("/me/email")
    @Operation(summary = "Change email (= username): current password verified, applied in Keycloak first, then stored. "
            + "Rejected for accounts signing in through an external provider")
    public ReturnOwnUserDTO updateEmail(AuthenticatedUser user, @Valid @RequestBody UpdateEmailDTO body) {
        assertLocalAccount(user);
        return userService.updateEmail(user.userId(), body.currentPassword(), body.email());
    }

    @PatchMapping("/me/locale")
    @Operation(summary = "Set the interface language. Stored here and mirrored into Keycloak "
            + "(best effort); allowed for external-provider accounts too")
    public ReturnOwnUserDTO updateLocale(AuthenticatedUser user, @Valid @RequestBody UpdateLocaleDTO body) {
        return userService.updateLocale(user.userId(), user.identityProvider(), body.locale());
    }

    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change password in-app: current password verified, then reset in Keycloak. "
            + "Rejected for accounts signing in through an external provider")
    public void updatePassword(AuthenticatedUser user, @Valid @RequestBody UpdatePasswordDTO body) {
        assertLocalAccount(user);
        userService.updatePassword(user.userId(), body.currentPassword(), body.newPassword());
    }

    /** Google/Apple accounts have no credentials here — the provider owns them. */
    private void assertLocalAccount(AuthenticatedUser user) {
        if (user.isExternallyManaged()) {
            throw new ApiException(ErrorCode.ACCOUNT_MANAGED_EXTERNALLY);
        }
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete own account and leave all groups (restorable by a fresh login within 30 days)")
    public void deleteMe(AuthenticatedUser user) {
        userService.softDeleteUser(user.userId());
    }

    @GetMapping("/me/export")
    @Operation(summary = "Download all personal data of the own account as JSON (GDPR Art. 15/20)")
    public ResponseEntity<ReturnUserExportDTO> exportMe(AuthenticatedUser user) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("wishlist-export.json").build().toString())
                .body(exportService.export(user.userId()));
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
