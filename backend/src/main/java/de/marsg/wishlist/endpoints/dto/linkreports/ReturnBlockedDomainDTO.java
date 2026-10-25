package de.marsg.wishlist.endpoints.dto.linkreports;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record ReturnBlockedDomainDTO(
        @JsonProperty("id") UUID id,
        @JsonProperty("host") String host,
        @JsonProperty("blocked_at") Instant blockedAt,
        @JsonProperty("link_report_id") UUID linkReportId) {
}
