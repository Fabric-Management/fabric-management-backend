package com.fabricmanagement.shared.infrastructure.exception;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.domain.exception.*;
import com.fabricmanagement.shared.domain.message.AuthMessageKeys;
import com.fabricmanagement.shared.infrastructure.service.MessageResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Global Exception Handler
 * 
 * Centralized exception handling for all microservices.
 * Provides consistent error responses across the system.
 * 
 * This handler is conditionally enabled only if a service-specific 
 * exception handler is not present, allowing services to override
 * with their own custom exception handling while maintaining 
 * microservices autonomy.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
@ConditionalOnMissingBean(name = "serviceExceptionHandler")
public class GlobalExceptionHandler {
    
    private final MessageResolver messageResolver;

    /**
     * Handle verification code errors
     */
    @ExceptionHandler(InvalidVerificationCodeException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidVerificationCode(
            InvalidVerificationCodeException ex, WebRequest request) {
        
        String message = messageResolver.getMessage(AuthMessageKeys.VERIFICATION_CODE_INVALID);
        log.warn("Invalid verification code attempt");
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message, "INVALID_VERIFICATION_CODE"));
    }
    
    /**
     * Handle expired verification code
     */
    @ExceptionHandler(VerificationCodeExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleVerificationCodeExpired(
            VerificationCodeExpiredException ex, WebRequest request) {
        
        String message = messageResolver.getMessage(AuthMessageKeys.VERIFICATION_CODE_EXPIRED);
        log.warn("Verification code expired");
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message, "VERIFICATION_CODE_EXPIRED"));
    }

    /**
     * Handle password not set (user tries to login before setting password)
     */
    @ExceptionHandler(PasswordNotSetException.class)
    public ResponseEntity<ApiResponse<Void>> handlePasswordNotSet(
            PasswordNotSetException ex, WebRequest request) {
        
        log.warn("Login attempt without password: {}", ex.getMessage());
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ex.getMessage(), "PASSWORD_NOT_SET"));
    }

    /**
     * Handle password already set (user tries to setup password again)
     */
    @ExceptionHandler(PasswordAlreadySetException.class)
    public ResponseEntity<ApiResponse<Void>> handlePasswordAlreadySet(
            PasswordAlreadySetException ex, WebRequest request) {
        
        log.warn("Password setup attempted for user who already has password: {}", ex.getMessage());
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ex.getMessage(), "PASSWORD_ALREADY_SET"));
    }

    /**
     * Handle validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationErrors(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        List<String> errors = new ArrayList<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.add(fieldName + ": " + errorMessage);
        });

        log.warn("Validation error: {}", errors);
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(
                    "Validation failed",
                    "VALIDATION_ERROR",
                    errors
                ));
    }

    /**
     * Handle business rule violations
     */
    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRuleViolation(
            BusinessRuleViolationException ex, WebRequest request) {
        
        log.warn("Business rule violation: {}", ex.getMessage());
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(
                    ex.getMessage(),
                    ex.getErrorCode()
                ));
    }

    /**
     * Handle entity not found
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEntityNotFound(
            EntityNotFoundException ex, WebRequest request) {
        
        log.warn("Entity not found: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                    ex.getMessage(),
                    "ENTITY_NOT_FOUND"
                ));
    }

    /**
     * Handle unauthorized access
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(
            UnauthorizedException ex, WebRequest request) {
        
        log.warn("Unauthorized access: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(
                    ex.getMessage(),
                    "UNAUTHORIZED"
                ));
    }

    /**
     * Handle forbidden access
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(
            ForbiddenException ex, WebRequest request) {
        
        log.warn("Forbidden access: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(
                    ex.getMessage(),
                    "FORBIDDEN"
                ));
    }

    /**
     * Handle duplicate entity
     */
    @ExceptionHandler(DuplicateEntityException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateEntity(
            DuplicateEntityException ex, WebRequest request) {
        
        log.warn("Duplicate entity: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                    ex.getMessage(),
                    "DUPLICATE_ENTITY"
                ));
    }

    /**
     * Handle tenant registration errors
     * 
     * Returns 400 Bad Request with user-friendly message.
     * Common scenarios:
     * - Email already registered
     * - Tax ID already exists
     * - Company name duplicate
     * - Invalid registration data
     */
    @ExceptionHandler(TenantRegistrationException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenantRegistrationError(
            TenantRegistrationException ex, WebRequest request) {
        
        log.warn("Tenant registration error: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                    ex.getMessage(),
                    "TENANT_REGISTRATION_ERROR"
                ));
    }

    /**
     * Handle external service errors
     */
    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleExternalServiceError(
            ExternalServiceException ex, WebRequest request) {
        
        log.error("External service error: {}", ex.getMessage(), ex);
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                    "External service temporarily unavailable",
                    "EXTERNAL_SERVICE_ERROR"
                ));
    }

    /**
     * Handle generic runtime exceptions
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                    "An unexpected error occurred",
                    "INTERNAL_SERVER_ERROR"
                ));
    }

    /**
     * Handle generic exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex, WebRequest request) {
        
        log.error("Generic error: {}", ex.getMessage(), ex);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                    "An unexpected error occurred",
                    "INTERNAL_SERVER_ERROR"
                ));
    }
}
