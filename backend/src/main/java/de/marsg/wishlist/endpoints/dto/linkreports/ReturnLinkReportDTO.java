package de.marsg.wishlist.endpoints.dto.linkreports;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.jpa.entities.LinkBlockSource;
import de.marsg.wishlist.jpa.entities.LinkReportReason;
import de.marsg.wishlist.jpa.entities.LinkReportStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Admin queue entry. Link and wish fields come from the live rows, or from the evidence
 * snapshot once those are gone (link_id is null then); null when neither exists.
 */
public record ReturnLinkReportDTO(
        @JsonProperty("id") UUID id,
        @JsonProperty("status") LinkReportStatus status,
        @JsonProperty("reason") LinkReportReason reason,
        @JsonProperty("comment") String comment,
        @JsonProperty("reported_at") Instant reportedAt,
        @JsonProperty("link_id") UUID linkId,
        @JsonProperty("url") String url,
        @JsonProperty("host") String host,
        @JsonProperty("blocked_by") LinkBlockSource blockedBy,
        @JsonProperty("wish_id") UUID wishId,
        @JsonProperty("wish_title") String wishTitle,
        @JsonProperty("owner_displayname") String ownerDisplayname,
        @JsonProperty("reporter_displayname") String reporterDisplayname,
        @JsonProperty("decided_at") Instant decidedAt) {
}
