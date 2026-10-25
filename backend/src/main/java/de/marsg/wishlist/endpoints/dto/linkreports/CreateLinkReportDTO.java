package de.marsg.wishlist.endpoints.dto.linkreports;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.jpa.entities.LinkReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLinkReportDTO(
        @JsonProperty("reason") @NotNull LinkReportReason reason,
        @JsonProperty("comment") @Size(max = 500) String comment) {
}
