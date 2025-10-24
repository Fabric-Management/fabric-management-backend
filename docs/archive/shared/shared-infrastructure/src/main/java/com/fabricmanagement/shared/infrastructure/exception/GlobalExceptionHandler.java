package com.fabricmanagement.shared.infrastructure.exception;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.domain.message.MessageKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler
 * 
 * Centralized exception handling for all services
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ PRODUCTION-READY
 * ✅ GLOBAL EXCEPTION HANDLER
 * ✅ i18n SUPPORT
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {
    
    private final MessageResolver messageResolver;
    
    /**
     * Handle validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationErrors(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        log.warn("❌ Validation error: {}", errors);
        
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(
                messageResolver.resolve(MessageKeys.VALIDATION_INVALID_FORMAT),
                errors
            ));
    }
    
    /**
     * Handle business rule violations
     */
    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRuleViolation(
            BusinessRuleViolationException ex, WebRequest request) {
        
        log.warn("❌ Business rule violation: {}", ex.getMessage());
        
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(
                messageResolver.resolve(MessageKeys.BUSINESS_RULE_VIOLATION, ex.getMessage())
            ));
    }
    
    /**
     * Handle duplicate entity errors
     */
    @ExceptionHandler(DuplicateEntityException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateEntity(
            DuplicateEntityException ex, WebRequest request) {
        
        log.warn("❌ Duplicate entity: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(
                messageResolver.resolve(MessageKeys.BUSINESS_RESOURCE_CONFLICT, ex.getMessage())
            ));
    }
    
    /**
     * Handle entity not found errors
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEntityNotFound(
            EntityNotFoundException ex, WebRequest request) {
        
        log.warn("❌ Entity not found: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(
                messageResolver.resolve(MessageKeys.ERROR_NOT_FOUND, ex.getMessage())
            ));
    }
    
    /**
     * Handle external service errors
     */
    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleExternalServiceError(
            ExternalServiceException ex, WebRequest request) {
        
        log.error("❌ External service error: {}", ex.getMessage(), ex);
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResponse.error(
                messageResolver.resolve(MessageKeys.ERROR_SERVICE_UNAVAILABLE, ex.getMessage())
            ));
    }
    
    /**
     * Handle forbidden access errors
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(
            ForbiddenException ex, WebRequest request) {
        
        log.warn("❌ Forbidden access: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error(
                messageResolver.resolve(MessageKeys.ERROR_FORBIDDEN, ex.getMessage())
            ));
    }
    
    /**
     * Handle unauthorized access errors
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(
            UnauthorizedException ex, WebRequest request) {
        
        log.warn("❌ Unauthorized access: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error(
                messageResolver.resolve(MessageKeys.ERROR_UNAUTHORIZED, ex.getMessage())
            ));
    }
    
    /**
     * Handle illegal argument errors
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        
        log.warn("❌ Illegal argument: {}", ex.getMessage());
        
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(
                messageResolver.resolve(MessageKeys.ERROR_BAD_REQUEST, ex.getMessage())
            ));
    }
    
    /**
     * Handle illegal state errors
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(
            IllegalStateException ex, WebRequest request) {
        
        log.warn("❌ Illegal state: {}", ex.getMessage());
        
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(
                messageResolver.resolve(MessageKeys.BUSINESS_INVALID_OPERATION, ex.getMessage())
            ));
    }
    
    /**
     * Handle generic exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex, WebRequest request) {
        
        log.error("❌ Unexpected error: {}", ex.getMessage(), ex);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(
                messageResolver.resolve(MessageKeys.ERROR_INTERNAL_SERVER)
            ));
    }
    
    /**
     * Handle runtime exceptions
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        
        log.error("❌ Runtime error: {}", ex.getMessage(), ex);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(
                messageResolver.resolve(MessageKeys.ERROR_INTERNAL_SERVER, ex.getMessage())
            ));
    }
}