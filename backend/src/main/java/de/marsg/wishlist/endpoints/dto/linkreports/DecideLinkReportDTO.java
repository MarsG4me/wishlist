package de.marsg.wishlist.endpoints.dto.linkreports;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/** Admin decision on a report; escalate (default false) is only valid together with a block action. */
public record DecideLinkReportDTO(
        @JsonProperty("action") @NotNull Action action,
        @JsonProperty("escalate") boolean escalate) {

    public enum Action {
        DISMISS,
        BLOCK_LINK,
        BLOCK_DOMAIN
    }
}
