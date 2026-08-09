package de.marsg.wishlist.endpoints.controller;

import de.marsg.wishlist.endpoints.dto.admin.ReturnAdminStatsDTO;
import de.marsg.wishlist.endpoints.dto.admin.ReturnAdminUserDTO;
import de.marsg.wishlist.endpoints.dto.common.ReturnListDTO;
import de.marsg.wishlist.endpoints.services.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ReturnListDTO<ReturnAdminUserDTO> searchUsers(@RequestParam("email") @NotBlank String email,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", required = false) Integer offset) {
        return adminService.searchByEmail(email, limit, offset);
    }
}
