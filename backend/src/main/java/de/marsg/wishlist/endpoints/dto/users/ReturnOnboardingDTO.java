package de.marsg.wishlist.endpoints.dto.users;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Counts behind the onboarding card; soft-deleted rows are not counted. */
public record ReturnOnboardingDTO(
        @JsonProperty("wishlist_total") long wishlistTotal,
        @JsonProperty("wish_total") long wishTotal,
        @JsonProperty("group_total") long groupTotal) {
}
