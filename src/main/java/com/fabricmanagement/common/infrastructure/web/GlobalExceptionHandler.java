package com.fabricmanagement.common.infrastructure.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for all REST controllers.
 *
 * <p>Provides consistent error responses across the entire API.
 * Handles common exceptions and transforms them into ApiResponse format.</p>
 *
 * <h2>Error Response Format:</h2>
 * <pre>{@code
 * {
 *   "success": false,
 *   "error": {
 *     "code": "VALIDATION_ERROR",
 *     "message": "Validation failed",
 *     "details": {
 *       "name": "Name is required",
 *       "price": "Price must be positive"
 *     }
 *   },
 *   "timestamp": "2025-01-27T10:30:00Z",
 *   "path": "/api/production/materials"
 * }
 * }</pre>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex,
            WebRequest request) {
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation error on {}: {}", request.getDescription(false), errors);

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .error(ApiResponse.ErrorDetail.builder()
                    .code("VALIDATION_ERROR")
                    .message("Validation failed: " + errors)
                    .build())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
            IllegalArgumentException ex,
            WebRequest request) {
        
        log.warn("Illegal argument on {}: {}", request.getDescription(false), ex.getMessage());

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .error(ApiResponse.ErrorDetail.builder()
                    .code("BAD_REQUEST")
                    .message(ex.getMessage())
                    .build())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex,
            WebRequest request) {
        
        log.warn("Authentication failed on {}: {}", request.getDescription(false), ex.getMessage());

        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .error(ApiResponse.ErrorDetail.builder()
                    .code("UNAUTHORIZED")
                    .message("Authentication failed")
                    .build())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex,
            WebRequest request) {
        
        log.warn("Access denied on {}: {}", request.getDescription(false), ex.getMessage());

        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .error(ApiResponse.ErrorDetail.builder()
                    .code("FORBIDDEN")
                    .message("Access denied")
                    .build())
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(
            Exception ex,
            WebRequest request) {
        
        log.error("Unexpected error on {}: {}", request.getDescription(false), ex.getMessage(), ex);

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.<Void>builder()
                .success(false)
                .error(ApiResponse.ErrorDetail.builder()
                    .code("INTERNAL_SERVER_ERROR")
                    .message("An unexpected error occurred")
                    .build())
                .timestamp(Instant.now())
                .build());
    }
}

