package de.marsg.wishlist.endpoints.controller;

import de.marsg.wishlist.configs.security.AuthenticatedUser;
import de.marsg.wishlist.endpoints.dto.common.ReturnListDTO;
import de.marsg.wishlist.endpoints.dto.linkreports.DecideLinkReportDTO;
import de.marsg.wishlist.endpoints.dto.linkreports.ReturnBlockedDomainDTO;
import de.marsg.wishlist.endpoints.dto.linkreports.ReturnLinkReportDTO;
import de.marsg.wishlist.endpoints.services.LinkReportAdminService;
import de.marsg.wishlist.endpoints.services.LinkReportAdminService.AuthorityReport;
import de.marsg.wishlist.jpa.entities.LinkReportStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin")
@PreAuthorize("hasRole('admin')")
@Validated
@Tag(name = "admin-link-reports")
public class LinkReportAdminController {

    private final LinkReportAdminService service;

    public LinkReportAdminController(LinkReportAdminService service) {
        this.service = service;
    }

    @GetMapping("/link-reports")
    @Operation(summary = "Reported links, newest first, optionally filtered by status (limit is clamped to 1..100)")
    public ReturnListDTO<ReturnLinkReportDTO> list(
            @RequestParam(value = "status", required = false) LinkReportStatus status,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", required = false) Integer offset) {
        return service.list(status, limit, offset);
    }

    @PostMapping("/link-reports/{id}/decision")
    @Operation(summary = "Dismiss a report, or block its link or domain; escalate (block actions only) "
            + "stores an evidence snapshot for the authorities")
    public ReturnLinkReportDTO decide(AuthenticatedUser user, @PathVariable("id") UUID id,
            @Valid @RequestBody DecideLinkReportDTO body) {
        return service.decide(user.userId(), id, body);
    }

    @GetMapping("/link-reports/{id}/authority-report")
    @Operation(summary = "Download the report of an escalated link report as JSON or printable HTML")
    public ResponseEntity<byte[]> authorityReport(AuthenticatedUser user, @PathVariable("id") UUID id,
            @RequestParam(value = "format", defaultValue = "json") @Pattern(regexp = "json|html") String format) {
        AuthorityReport report = service.authorityReport(user.userId(), id, format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(report.filename()).build().toString())
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
                .contentType(MediaType.parseMediaType(report.contentType()))
                .body(report.body());
    }

    @GetMapping("/blocked-domains")
    @Operation(summary = "Domains blocked by an admin, newest first (limit is clamped to 1..100)")
    public ReturnListDTO<ReturnBlockedDomainDTO> blockedDomains(
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", required = false) Integer offset) {
        return service.listBlockedDomains(limit, offset);
    }

    @DeleteMapping("/blocked-domains/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Lift a domain block; the domain's links are rescanned")
    public void unblockDomain(AuthenticatedUser user, @PathVariable("id") UUID id) {
        service.unblockDomain(user.userId(), id);
    }

    @PostMapping("/links/{id}/unblock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Lift the admin block of one link")
    public void unblockLink(AuthenticatedUser user, @PathVariable("id") UUID id) {
        service.unblockLink(user.userId(), id);
    }
}
