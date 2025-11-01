package com.fabricmanagement.common.infrastructure.web.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for all controllers.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFound(NotFoundException ex, HttpServletRequest req) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ApiError.of(404, "Not Found", "NOT_FOUND", ex.getMessage(), req.getRequestURI());
    }
    
    @ExceptionHandler(DomainException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleDomain(DomainException ex, HttpServletRequest req) {
        log.warn("Domain rule violation: {}", ex.getMessage());
        return ApiError.of(400, "Bad Request", "DOMAIN_RULE_VIOLATION", ex.getMessage(), req.getRequestURI());
    }
    
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleIllegal(RuntimeException ex, HttpServletRequest req) {
        log.warn("Illegal argument/state: {}", ex.getMessage());
        return ApiError.of(400, "Bad Request", "ILLEGAL_ARGUMENT", ex.getMessage(), req.getRequestURI());
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                fieldError -> fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "",
                (existing, replacement) -> existing
            ));
        
        log.warn("Validation failed: {}", fieldErrors);
        return ApiError.of(400, "Bad Request", "VALIDATION_ERROR", "Validation failed", req.getRequestURI(), fieldErrors);
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, String> details = ex.getConstraintViolations()
            .stream()
            .collect(Collectors.toMap(
                v -> v.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (existing, replacement) -> existing
            ));
        
        log.warn("Constraint violation: {}", details);
        return ApiError.of(400, "Bad Request", "CONSTRAINT_VIOLATION", "Validation failed", req.getRequestURI(), details);
    }
    
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        log.warn("Request body not readable: {}", ex.getMessage());
        return ApiError.of(400, "Bad Request", "BAD_REQUEST", "Request body is not readable", req.getRequestURI());
    }
    
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleOptimisticLock(Exception ex, HttpServletRequest req) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return ApiError.of(409, "Conflict", "OPTIMISTIC_LOCK",
            "Resource was updated by another transaction. Please retry.", req.getRequestURI());
    }
    
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        String msg = "Data conflict (unique or FK constraint).";
        return ApiError.of(409, "Conflict", "DATA_INTEGRITY", msg, req.getRequestURI());
    }
    
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error occurred: {}", req.getRequestURI(), ex);
        return ApiError.of(500, "Internal Server Error", "UNEXPECTED_ERROR",
            "An unexpected error occurred. Please contact support.", req.getRequestURI());
    }
}

