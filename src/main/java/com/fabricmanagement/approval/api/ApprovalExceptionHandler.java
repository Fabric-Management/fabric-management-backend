package com.fabricmanagement.approval.api;

import java.time.Instant;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Approval modülü için merkezi exception handler. İstemciye tutarlı hata formatı sunar ve entity
 * stack trace sızıntısını önler.
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.fabricmanagement.approval.api")
public class ApprovalExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
    log.warn("Bad request: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            Map.of(
                "status",
                400,
                "error",
                "Bad Request",
                "message",
                ex.getMessage(),
                "timestamp",
                Instant.now().toString()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
    log.warn("Conflict: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            Map.of(
                "status",
                409,
                "error",
                "Conflict",
                "message",
                ex.getMessage(),
                "timestamp",
                Instant.now().toString()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");

    log.warn("Validation error: {}", message);
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(
            Map.of(
                "status",
                422,
                "error",
                "Validation Failed",
                "message",
                message,
                "timestamp",
                Instant.now().toString()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
    log.error("Unexpected error in approval module", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            Map.of(
                "status",
                500,
                "error",
                "Internal Server Error",
                "message",
                "An unexpected error occurred. Please contact support.",
                "timestamp",
                Instant.now().toString()));
  }
}
