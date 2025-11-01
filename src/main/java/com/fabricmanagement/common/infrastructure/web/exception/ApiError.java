package com.fabricmanagement.common.infrastructure.web.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Standard API error response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;
    private int status;
    private String error;
    private String code;
    private String message;
    private String path;
    private Map<String, Object> details;
    
    public static ApiError of(int status, String error, String code, String message, String path) {
        return of(status, error, code, message, path, Collections.emptyMap());
    }
    
    @SuppressWarnings("unchecked")
    public static ApiError of(int status, String error, String code, String message, String path, Map<String, ?> details) {
        ApiError apiError = new ApiError();
        apiError.timestamp = Instant.now();
        apiError.status = status;
        apiError.error = error;
        apiError.code = code;
        apiError.message = message;
        apiError.path = path;
        apiError.details = (Map<String, Object>) details;
        return apiError;
    }
}

