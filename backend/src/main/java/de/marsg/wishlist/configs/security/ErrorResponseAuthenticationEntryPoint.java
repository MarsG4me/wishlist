package de.marsg.wishlist.configs.security;

import de.marsg.wishlist.endpoints.dto.common.ErrorResponseDTO;
import de.marsg.wishlist.helpers.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;

/**
 * Security-filter-level 401s never reach the MVC exception handler; this keeps them
 * in the same { error_code, message } shape as every other error.
 */
public class ErrorResponseAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ErrorResponseAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setStatus(ErrorCode.UNAUTHENTICATED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ErrorResponseDTO.of(ErrorCode.UNAUTHENTICATED));
    }
}
