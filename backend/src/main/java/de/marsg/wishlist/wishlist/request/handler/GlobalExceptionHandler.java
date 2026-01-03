package de.marsg.wishlist.wishlist.request.handler;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import de.marsg.wishlist.wishlist.logging.LogMgr;

@ControllerAdvice
public class GlobalExceptionHandler {

    private final LogMgr logger;

    public GlobalExceptionHandler(LogMgr logger) {
        this.logger = logger;
    }

    /**
     * Handles validation errors triggered by @Valid annotations in request bodies.
     * Collects all field-level errors and returns them as a map in the response.
     * Returns HTTP 400 Bad Request.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));

        logger.logFine("Validation error: %s", errors.toString());
        return ResponseEntity.badRequest().body(errors);
    }

    /**
     * Handles cases where the request body is missing or is invalid JSON.
     * Returns HTTP 400 Bad Request.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleMissingBody(HttpMessageNotReadableException e) {
        logger.logFine("Missing or unreadable request body: %s", e.getMessage());
        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "error", "Request body is missing or malformed JSON.",
                        "errId", "MISSING_REQUEST_BODY"));
    }

    /**
     * Handles requests that use an unsupported HTTP method for a given endpoint.
     * Returns HTTP 405 Method Not Allowed with an 'Allow' header listing permitted methods.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {

        HttpHeaders headers = new HttpHeaders();
        headers.setAllow(ex.getSupportedHttpMethods()); // sets the Allow header with permitted methods

        String body = String.format(
                "HTTP method '%s' is not supported for this endpoint. Supported methods: %s",
                ex.getMethod(),
                ex.getSupportedHttpMethods());

        return ResponseEntity.status(405).headers(headers).body(Map.of(
                "error", body));
    }

    /**
     * Handles IllegalArgumentException, which should mostly occure when the mistake is in the code itself. 
     * Returns HTTP 400 Bad Request
     * with the exception message.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        logger.logInfo("Illegal Argument error: %s", e.getMessage());
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    /**
     * Catch all unhandled exceptions.
     * Logs the error as severe but does not expose sensitive details to the client.
     * This error for us indicates a problem which was not handled yet and should be fixed.
     * Returns HTTP 500 Internal Server Error.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleAll(Exception e) {
        logger.logSevere("Unexpected error: %s", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error");
    }
}
