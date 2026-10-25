package de.marsg.wishlist.endpoints.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.helpers.ErrorCode;

/**
 * Shape of EVERY error response. {@code error_code} is stable and translated by the
 * frontend; {@code message} is English for direct API users.
 */
public record ErrorResponseDTO(
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("message") String message,
        @JsonProperty("link_index") @JsonInclude(JsonInclude.Include.NON_NULL) Integer linkIndex) {

    public static ErrorResponseDTO of(ErrorCode code) {
        return new ErrorResponseDTO(code.name(), code.getMessage(), null);
    }

    public static ErrorResponseDTO of(ErrorCode code, String message) {
        return new ErrorResponseDTO(code.name(), message, null);
    }

    public static ErrorResponseDTO of(ErrorCode code, String message, Integer linkIndex) {
        return new ErrorResponseDTO(code.name(), message, linkIndex);
    }
}
