package com.fabricmanagement.shared.application.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Standardized API Response
 * 
 * All API responses follow this consistent structure for better
 * client integration and error handling.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * Indicates if the operation was successful
     */
    private Boolean success;

    /**
     * Human-readable message describing the result
     */
    private String message;

    /**
     * The actual data payload
     */
    private T data;

    /**
     * Error code for programmatic error handling
     */
    private String errorCode;

    /**
     * List of validation errors or detailed error messages
     */
    private List<String> errors;

    /**
     * Response timestamp
     */
    private LocalDateTime timestamp;

    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;

    /**
     * Create a successful response
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("Operation successful")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create a successful response with custom message
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create an error response
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create an error response with error code
     */
    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create an error response with validation errors
     */
    public static <T> ApiResponse<T> error(String message, String errorCode, List<String> errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .errors(errors)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create a paginated response
     */
    public static <T> ApiResponse<PaginatedResponse<T>> paginated(
            List<T> data, int page, int size, long totalElements, int totalPages) {
        
        PaginatedResponse<T> paginatedData = PaginatedResponse.<T>builder()
                .content(data)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(page == 0)
                .last(page == totalPages - 1)
                .build();

        return ApiResponse.<PaginatedResponse<T>>builder()
                .success(true)
                .message("Data retrieved successfully")
                .data(paginatedData)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Paginated response wrapper
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaginatedResponse<T> {
        private List<T> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean first;
        private boolean last;

        public boolean isHasNext() {
            return !last;
        }

        public boolean isHasPrevious() {
            return !first;
        }
    }
}
