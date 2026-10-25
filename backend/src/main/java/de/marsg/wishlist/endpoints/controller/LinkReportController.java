package de.marsg.wishlist.endpoints.controller;

import de.marsg.wishlist.configs.security.AuthenticatedUser;
import de.marsg.wishlist.endpoints.dto.linkreports.CreateLinkReportDTO;
import de.marsg.wishlist.endpoints.services.LinkReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/links")
@Tag(name = "links")
public class LinkReportController {

    private final LinkReportService reportService;

    public LinkReportController(LinkReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/{id}/reports")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Report a link of a wish you can see but do not own; one report per user and link")
    public void report(AuthenticatedUser user, @PathVariable("id") UUID id,
            @Valid @RequestBody CreateLinkReportDTO body) {
        reportService.report(user.userId(), id, body);
    }
}
