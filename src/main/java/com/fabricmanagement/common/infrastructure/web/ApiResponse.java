package com.fabricmanagement.common.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standard API response wrapper for all REST endpoints.
 *
 * <p>Provides consistent response structure across the entire API surface.
 * All endpoints should return data wrapped in ApiResponse for uniformity.</p>
 *
 * <h2>Success Response Example:</h2>
 * <pre>{@code
 * {
 *   "success": true,
 *   "data": { "id": "123", "name": "Material A" },
 *   "message": "Material created successfully",
 *   "timestamp": "2025-01-27T10:30:00Z"
 * }
 * }</pre>
 *
 * <h2>Error Response Example:</h2>
 * <pre>{@code
 * {
 *   "success": false,
 *   "error": {
 *     "code": "VALIDATION_ERROR",
 *     "message": "Invalid material name"
 *   },
 *   "timestamp": "2025-01-27T10:30:00Z"
 * }
 * }</pre>
 *
 * <h2>Usage in Controllers:</h2>
 * <pre>{@code
 * @PostMapping
 * public ResponseEntity<ApiResponse<MaterialDto>> create(@RequestBody CreateMaterialRequest request) {
 *     MaterialDto created = materialService.create(request);
 *     return ResponseEntity.ok(ApiResponse.success(created, "Material created successfully"));
 * }
 * }</pre>
 *
 * @param <T> the data type
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String message;
    private ErrorDetail error;
    
    @Builder.Default
    private Instant timestamp = Instant.now();

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .timestamp(Instant.now())
            .build();
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .message(message)
            .timestamp(Instant.now())
            .build();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
            .success(false)
            .error(ErrorDetail.builder()
                .code(code)
                .message(message)
                .build())
            .timestamp(Instant.now())
            .build();
    }

    public static <T> ApiResponse<T> error(ErrorDetail errorDetail) {
        return ApiResponse.<T>builder()
            .success(false)
            .error(errorDetail)
            .timestamp(Instant.now())
            .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetail {
        private String code;
        private String message;
        private String field;
        private Object rejectedValue;
    }
}

