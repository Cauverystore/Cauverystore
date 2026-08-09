package com.cauverystore.exception;

import io.jsonwebtoken.ExpiredJwtException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    /**
     * A suspended account trying to start something new.
     *
     * The message is written to be shown to the person it happened to - it says what they cannot
     * do, why, and what they can still do - so it is passed through rather than replaced with a
     * generic refusal.
     */
    @ExceptionHandler(com.cauverystore.service.AccountRestrictionService.AccountRestrictedException.class)
    public ResponseEntity<Map<String, String>> handleAccountRestricted(
            com.cauverystore.service.AccountRestrictionService.AccountRestrictedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleSpringAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied"));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<Map<String, String>> handleAuthFailed(AuthenticationFailedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(PasswordResetRequiredException.class)
    public ResponseEntity<Map<String, Object>> handlePasswordResetRequired(PasswordResetRequiredException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "error", ex.getMessage(),
                        "passwordResetRequired", true,
                        "email", ex.getEmail()
                ));
    }

    @ExceptionHandler(TokenException.class)
    public ResponseEntity<Map<String, String>> handleToken(TokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Declared explicitly rather than left to the RuntimeException handler below, which
     * replaces any message over 200 characters with "An unexpected error occurred". This one
     * has to survive intact: it names the offending code and tells the seller how to find the
     * right one, and swallowing that would leave them staring at a save button that fails for
     * no stated reason.
     */
    @ExceptionHandler(com.cauverystore.service.HsnClassificationService.UnknownHsnException.class)
    public ResponseEntity<Map<String, String>> handleUnknownHsn(
            com.cauverystore.service.HsnClassificationService.UnknownHsnException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(com.cauverystore.service.HsnClassificationService.UnclassifiedProductException.class)
    public ResponseEntity<Map<String, String>> handleUnclassifiedProduct(
            com.cauverystore.service.HsnClassificationService.UnclassifiedProductException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.contains("Exception") || msg.contains("Error") || msg.length() > 200) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "An unexpected error occurred"));
        }
        return ResponseEntity.badRequest()
                .body(Map.of("error", msg));
    }

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<Map<String, String>> handleExpiredJwt(ExpiredJwtException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Token expired"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", ex.getMessage()));
    }
}
