package com.ecommerce.portal.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Centralized error handling: every domain/framework exception is mapped to
 * an HTTP status + {@link ErrorResponse} in exactly one place, instead of a
 * try/catch in every controller method. Nothing here ever echoes a stack
 * trace or internal exception detail back to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ServiceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleServiceNotFound(ServiceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("SERVICE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateServiceNameException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateServiceName(DuplicateServiceNameException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("SERVICE_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateGrantException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateGrant(DuplicateGrantException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("GRANT_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(NotAProducerException.class)
    public ResponseEntity<ErrorResponse> handleNotAProducer(NotAProducerException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("NOT_A_PRODUCER", ex.getMessage()));
    }

    @ExceptionHandler(NotAConsumerException.class)
    public ResponseEntity<ErrorResponse> handleNotAConsumer(NotAConsumerException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("NOT_A_CONSUMER", ex.getMessage()));
    }

    /** Bad clientId/clientSecret at the token endpoint -> 401, per RFC 6749's invalid_client. */
    @ExceptionHandler(InvalidClientCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidClientCredentials(InvalidClientCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("INVALID_CLIENT", ex.getMessage()));
    }

    @ExceptionHandler(UnknownAudienceException.class)
    public ResponseEntity<ErrorResponse> handleUnknownAudience(UnknownAudienceException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("UNKNOWN_AUDIENCE", ex.getMessage()));
    }

    @ExceptionHandler(GrantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleGrantNotFound(GrantNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("GRANT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(NoRequestedScopesGrantedException.class)
    public ResponseEntity<ErrorResponse> handleNoRequestedScopesGranted(NoRequestedScopesGrantedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("SCOPE_NOT_GRANTED", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    /** Malformed/unparseable JSON body or wrong Content-Type -> 400, not a 500. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", "Malformed or missing request body"));
    }

    /** Catch-all safety net: log the real cause server-side, leak nothing client-side. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
