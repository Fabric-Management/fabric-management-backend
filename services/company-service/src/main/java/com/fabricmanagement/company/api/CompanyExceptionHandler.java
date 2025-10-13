package com.fabricmanagement.company.api;

import com.fabricmanagement.company.domain.exception.CompanyAlreadyExistsException;
import com.fabricmanagement.company.domain.exception.CompanyNotFoundException;
import com.fabricmanagement.company.domain.exception.MaxUsersLimitException;
import com.fabricmanagement.company.domain.exception.UnauthorizedCompanyAccessException;
import com.fabricmanagement.shared.application.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Company Service Exception Handler
 *
 * Service-specific exception handling for company-service.
 * When this bean is present, the shared GlobalExceptionHandler is disabled,
 * maintaining microservices autonomy and loose coupling.
 */
@Component("serviceExceptionHandler")
@RestControllerAdvice
@Slf4j
public class CompanyExceptionHandler {
    
    @ExceptionHandler(CompanyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCompanyNotFound(CompanyNotFoundException ex) {
        log.error("Company not found: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.NOT_FOUND.value())
            .error("Not Found")
            .message(ex.getMessage())
            .build();
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(CompanyAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleCompanyAlreadyExists(CompanyAlreadyExistsException ex) {
        log.warn("Company already exists - Type: {}, Value: {}, Message: {}", 
            ex.getMatchType(), ex.getMatchedValue(), ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                    ex.getMessage(),
                    "COMPANY_ALREADY_EXISTS"
                ));
    }
    
    @ExceptionHandler(UnauthorizedCompanyAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedAccess(UnauthorizedCompanyAccessException ex) {
        log.error("Unauthorized access: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.FORBIDDEN.value())
            .error("Forbidden")
            .message(ex.getMessage())
            .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
    
    @ExceptionHandler(MaxUsersLimitException.class)
    public ResponseEntity<ErrorResponse> handleMaxUsersLimit(MaxUsersLimitException ex) {
        log.error("Max users limit exceeded: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Bad Request")
            .message(ex.getMessage())
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Failed")
            .message("Invalid input")
            .details(errors)
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.error("Invalid argument: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Bad Request")
            .message(ex.getMessage())
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * Handle constraint violations (e.g. @Min, @Max on request params)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String fieldName = violation.getPropertyPath().toString();
            String errorMessage = violation.getMessage();
            errors.put(fieldName, errorMessage);
        }
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Failed")
            .message("Invalid request parameters")
            .details(errors)
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * Handle type mismatch errors (e.g. invalid UUID format)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String paramName = ex.getName();
        
        Class<?> requiredTypeClass = ex.getRequiredType();
        String requiredType = (requiredTypeClass != null) ? requiredTypeClass.getSimpleName() : "unknown";
        
        Object value = ex.getValue();
        String providedValue = (value != null) ? value.toString() : "null";
        
        log.error("Type mismatch: parameter '{}' expected {}, got: {}", paramName, requiredType, providedValue);
        
        Map<String, String> details = new HashMap<>();
        details.put("parameter", paramName);
        details.put("expectedType", requiredType);
        details.put("providedValue", providedValue);
        
        String message = String.format("Invalid value '%s' for parameter '%s'. Expected type: %s", 
                                       providedValue, paramName, requiredType);
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Type Mismatch")
            .message(message)
            .details(details)
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * Handle missing required request parameters
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        String paramName = ex.getParameterName();
        String paramType = ex.getParameterType();
        
        log.error("Missing required parameter: {} (type: {})", paramName, paramType);
        
        Map<String, String> details = new HashMap<>();
        details.put("parameter", paramName);
        details.put("type", paramType);
        
        String message = String.format("Required parameter '%s' is missing", paramName);
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Missing Parameter")
            .message(message)
            .details(details)
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * Handle malformed JSON or request body parse errors
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.error("Malformed request body: {}", ex.getMessage());
        
        String message = "Malformed JSON request";
        
        // Try to extract specific error from exception
        if (ex.getCause() != null && ex.getCause().getMessage() != null) {
            String causeMessage = ex.getCause().getMessage();
            if (causeMessage.contains("Cannot deserialize")) {
                message = "Invalid request format. Please check your JSON structure.";
            } else if (causeMessage.contains("Unexpected character")) {
                message = "Invalid JSON syntax";
            }
        }
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Bad Request")
            .message(message)
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Internal Server Error")
            .message("An unexpected error occurred")
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ErrorResponse {
        private LocalDateTime timestamp;
        private int status;
        private String error;
        private String message;
        private Map<String, String> details;
    }
}

