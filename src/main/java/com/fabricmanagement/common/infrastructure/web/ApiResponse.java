package com.fabricmanagement.common.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard API response wrapper for all REST endpoints.
 *
 * <p>Provides consistent response structure across the entire API surface. All endpoints should
 * return data wrapped in ApiResponse for uniformity.
 *
 * <h2>Success Response Example:</h2>
 *
 * <pre>{@code
 * {
 *   "success": true,
 *   "data": { "id": "123", "name": "Product A" },
 *   "message": "Product created successfully",
 *   "timestamp": "2025-01-27T10:30:00Z"
 * }
 * }</pre>
 *
 * <h2>Success with Warnings (e.g. batch certification):</h2>
 *
 * <p>{@code warnings} is an optional string array, present when the operation succeeded but the
 * client should be informed (e.g. referenced supplier/facility certification expired for GOTS).
 *
 * <pre>{@code
 * {
 *   "success": true,
 *   "data": { "id": "...", ... },
 *   "warnings": [ "Referenced supplier certification has expired (validUntil: 2024-01-15)." ],
 *   "timestamp": "2025-01-27T10:30:00Z"
 * }
 * }</pre>
 *
 * <h2>Error Response Example:</h2>
 *
 * <pre>{@code
 * {
 *   "success": false,
 *   "error": {
 *     "code": "VALIDATION_ERROR",
 *     "message": "Invalid product name"
 *   },
 *   "timestamp": "2025-01-27T10:30:00Z"
 * }
 * }</pre>
 *
 * <h2>Usage in Controllers:</h2>
 *
 * <pre>{@code
 * @PostMapping
 * public ResponseEntity<ApiResponse<ProductDto>> create(@RequestBody CreateProductRequest request) {
 *     ProductDto created = productService.create(request);
 *     return ResponseEntity.ok(ApiResponse.success(created, "Product created successfully"));
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

  /** Optional warnings (e.g. expired referenced certification). Omitted when null or empty. */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private List<String> warnings;

  private ErrorDetail error;

  @Builder.Default private Instant timestamp = Instant.now();

  public static <T> ApiResponse<T> success(T data) {
    return ApiResponse.<T>builder().success(true).data(data).timestamp(Instant.now()).build();
  }

  public static <T> ApiResponse<T> success(T data, String message) {
    return ApiResponse.<T>builder()
        .success(true)
        .data(data)
        .message(message)
        .timestamp(Instant.now())
        .build();
  }

  /** Success with optional warnings (e.g. GOTS: referenced supplier/facility cert expired). */
  public static <T> ApiResponse<T> success(T data, List<String> warnings) {
    return ApiResponse.<T>builder()
        .success(true)
        .data(data)
        .warnings(warnings != null ? warnings : Collections.emptyList())
        .timestamp(Instant.now())
        .build();
  }

  public static <T> ApiResponse<T> error(String code, String message) {
    return ApiResponse.<T>builder()
        .success(false)
        .error(ErrorDetail.builder().code(code).message(message).build())
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
