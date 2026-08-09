package de.marsg.wishlist.endpoints.dto.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.marsg.wishlist.helpers.ErrorCode;

/**
 * Shape of EVERY error response. {@code error_code} is stable and translated by the
 * frontend; {@code message} is English for direct API users.
 */
public record ErrorResponseDTO(
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("message") String message) {

    public static ErrorResponseDTO of(ErrorCode code) {
        return new ErrorResponseDTO(code.name(), code.getMessage());
    }

    public static ErrorResponseDTO of(ErrorCode code, String message) {
        return new ErrorResponseDTO(code.name(), message);
    }
}
