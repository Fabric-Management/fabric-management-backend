package com.fabricmanagement.common.core.infrastructure.web.controller;

import com.fabricmanagement.common.core.application.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Base controller providing common functionality for all service controllers
 */
public abstract class BaseController {

    /**
     * Creates a successful response with data
     */
    protected <T> ResponseEntity<ApiResponse<T>> success(T data) {
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * Creates a successful response with message and data
     */
    protected <T> ResponseEntity<ApiResponse<T>> success(String message, T data) {
        return ResponseEntity.ok(ApiResponse.success(message, data));
    }

    /**
     * Creates a created response with data
     */
    protected <T> ResponseEntity<ApiResponse<T>> created(T data) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Resource created successfully", data));
    }

    /**
     * Creates a no content response
     */
    protected ResponseEntity<ApiResponse<Void>> noContent() {
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .body(ApiResponse.success("Operation completed successfully", null));
    }

    /**
     * Creates a bad request response
     */
    protected <T> ResponseEntity<ApiResponse<T>> badRequest(String error) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Bad Request", error));
    }

    /**
     * Creates a not found response
     */
    protected <T> ResponseEntity<ApiResponse<T>> notFound(String error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Resource not found", error));
    }

    /**
     * Creates an internal server error response
     */
    protected <T> ResponseEntity<ApiResponse<T>> internalServerError(String error) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal Server Error", error));
    }
}
