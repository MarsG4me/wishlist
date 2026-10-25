package de.marsg.wishlist.endpoints.controller;

import de.marsg.wishlist.configs.security.AuthenticatedUser;
import de.marsg.wishlist.endpoints.dto.admin.ReturnAdminStatsDTO;
import de.marsg.wishlist.endpoints.dto.admin.ReturnAdminUserDTO;
import de.marsg.wishlist.endpoints.dto.common.ReturnListDTO;
import de.marsg.wishlist.endpoints.dto.users.ReturnUserExportDTO;
import de.marsg.wishlist.endpoints.services.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin")
@PreAuthorize("hasRole('admin')")
@Validated
@Tag(name = "admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    @Operation(summary = "Aggregate counts (users, wishlists, wishes) — numbers only, no content access")
    public ReturnAdminStatsDTO stats() {
        return adminService.stats();
    }

    @GetMapping("/users")
    @Operation(summary = "Search accounts by email (partial match) for tier management")
    public ReturnListDTO<ReturnAdminUserDTO> searchUsers(AuthenticatedUser user,
            @RequestParam("email") @NotBlank @Size(max = 255) String email,
            @RequestParam(value = "include_deleted", defaultValue = "false") boolean includeDeleted,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", required = false) Integer offset) {
        return adminService.searchByEmail(user.userId(), email, includeDeleted, limit, offset);
    }

    @GetMapping("/users/{id}/export")
    @Operation(summary = "GDPR export of any account's personal data, soft-deleted ones included")
    public ResponseEntity<ReturnUserExportDTO> exportUser(AuthenticatedUser user, @PathVariable("id") UUID id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("wishlist-export.json").build().toString())
                .body(adminService.exportUser(user.userId(), id));
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an account: soft delete by default, immediate erasure "
            + "(database and Keycloak) with immediate=true")
    public void deleteUser(AuthenticatedUser user, @PathVariable("id") UUID id,
            @RequestParam(value = "immediate", defaultValue = "false") boolean immediate) {
        adminService.deleteUser(user.userId(), id, immediate);
    }
}
