package de.marsg.wishlist.endpoints.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Aggregate counts only — admins get numbers, never access to the content itself. */
public record ReturnAdminStatsDTO(
        @JsonProperty("user_count") long userCount,
        @JsonProperty("wishlist_count") long wishlistCount,
        @JsonProperty("wish_count") long wishCount) {
}
