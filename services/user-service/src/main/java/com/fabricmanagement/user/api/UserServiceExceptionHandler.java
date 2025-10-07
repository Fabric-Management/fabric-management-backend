package com.fabricmanagement.user.api;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.domain.exception.*;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * User Service Exception Handler
 * 
 * Service-specific exception handling for user-service.
 * When this bean is present, the shared GlobalExceptionHandler is disabled,
 * maintaining microservices autonomy and loose coupling.
 */
@Component("serviceExceptionHandler")
@RestControllerAdvice
@Slf4j
public class UserServiceExceptionHandler {

    /**
     * Handle Contact Not Found Exception
     */
    @ExceptionHandler(ContactNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleContactNotFound(ContactNotFoundException ex) {
        log.warn("Contact not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    /**
     * Handle User Not Found Exception
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(UserNotFoundException ex) {
        log.warn("User not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    /**
     * Handle Invalid Password Exception
     */
    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidPassword(InvalidPasswordException ex) {
        log.warn("Invalid password attempt");
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid credentials", ex.getErrorCode()));
    }

    /**
     * Handle Account Locked Exception
     */
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountLocked(AccountLockedException ex) {
        log.warn("Account locked: {}", ex.getMessage());
        
        // Message already contains remaining minutes info
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    /**
     * Handle Contact Not Verified Exception
     */
    @ExceptionHandler(ContactNotVerifiedException.class)
    public ResponseEntity<ApiResponse<Void>> handleContactNotVerified(ContactNotVerifiedException ex) {
        log.warn("Contact not verified: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    /**
     * Handle Invalid User Status Exception
     */
    @ExceptionHandler(InvalidUserStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidUserStatus(InvalidUserStatusException ex) {
        log.warn("Invalid user status: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    /**
     * Handle Password Already Set Exception
     */
    @ExceptionHandler(PasswordAlreadySetException.class)
    public ResponseEntity<ApiResponse<Void>> handlePasswordAlreadySet(PasswordAlreadySetException ex) {
        log.warn("Password already set: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    /**
     * Handle Validation Errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        log.warn("Validation errors: {}", errors);
        
        // Build detailed error message from validation errors
        String detailedMessage = "Validation failed: " + errors.toString();
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(detailedMessage, "VALIDATION_ERROR"));
    }

    /**
     * Handle Feign Client Exceptions (Service-to-Service communication errors)
     */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiResponse<Void>> handleFeignException(FeignException ex) {
        log.error("Service communication error: {}", ex.getMessage());
        
        if (ex.status() == 404) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Resource not found in downstream service", "SERVICE_NOT_FOUND"));
        } else if (ex.status() == 503) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Service temporarily unavailable. Please try again later.", "SERVICE_UNAVAILABLE"));
        } else {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("An error occurred while communicating with another service", "SERVICE_ERROR"));
        }
    }

    /**
     * Handle Illegal Argument Exceptions
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), "INVALID_ARGUMENT"));
    }

    /**
     * Handle Illegal State Exceptions
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage(), "INVALID_STATE"));
    }

    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again later.", "INTERNAL_ERROR"));
    }
}
