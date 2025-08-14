package com.fabricmanagement.common.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        ApiError error,
        LocalDateTime timestamp
) {

    // Success factory methods
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "Operation successful", data, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, null, LocalDateTime.now());
    }

    // Error factory methods
    public static <T> ApiResponse<T> error(String message, String errorCode) {
        ApiError error = new ApiError(errorCode, message, null);
        return new ApiResponse<>(false, message, null, error, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(ApiError error) {
        return new ApiResponse<>(false, error.message(), null, error, LocalDateTime.now());
    }
}