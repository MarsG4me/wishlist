package de.marsg.wishlist.endpoints.controller;

import de.marsg.wishlist.endpoints.dto.invites.ReturnInvitePreviewDTO;
import de.marsg.wishlist.endpoints.services.InviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/invites")
@Tag(name = "invites")
public class InviteController {

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @GetMapping("/{code}/preview")
    @Operation(summary = "Public preview of an invite; membership flags only with a valid bearer token")
    public ReturnInvitePreviewDTO preview(@PathVariable String code) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID keycloakId = null;
        if (auth instanceof JwtAuthenticationToken jwt) {
            try {
                keycloakId = UUID.fromString(jwt.getToken().getSubject());
            } catch (IllegalArgumentException | NullPointerException e) {
                keycloakId = null;
            }
        }
        return inviteService.preview(code, keycloakId);
    }
}
