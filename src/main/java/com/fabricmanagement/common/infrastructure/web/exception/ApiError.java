package com.fabricmanagement.common.infrastructure.web.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable error response body returned by {@link GlobalExceptionHandler} for all non-2xx
 * responses.
 *
 * <p>This record is the single, canonical error contract between the backend and any API consumer.
 * The JSON shape is fixed and must not be changed without updating the frontend types in {@code
 * types/api.ts → BackendErrorResponse}.
 *
 * <h2>Wire Format</h2>
 *
 * <pre>
 * {
 *   "timestamp": "2026-03-05T10:23:41.123Z",
 *   "status":    409,
 *   "error":     "Conflict",
 *   "code":      "RECIPE_IN_USE",
 *   "args":      [],
 *   "message":   "Fiber 'COT60_LIN40' composition cannot be changed ...",
 *   "path":      "/api/production/fibers/3fa85f64-...",
 *   "details": {
 *     "fiberId":   "3fa85f64-...",
 *     "fiberName": "COT60_LIN40",
 *     "blockedBy": "RESERVED, IN_PROGRESS"
 *   }
 * }
 * </pre>
 *
 * <h2>Fields</h2>
 *
 * <ul>
 *   <li>{@code timestamp} — ISO-8601 instant of when the error occurred
 *   <li>{@code status} — HTTP status code (mirrors the HTTP response status)
 *   <li>{@code error} — HTTP status phrase (e.g. "Bad Request", "Conflict")
 *   <li>{@code code} — machine-readable error code for frontend switch/dispatch (e.g.
 *       "RECIPE_IN_USE"). Never null.
 *   <li>{@code args} — dynamic arguments for parameterized frontend messages. Frontend usage:
 *       {@code t(error.code, { min: error.args[0] })}. Never null (may be empty array).
 *   <li>{@code message} — human-readable description, safe to show to the end user
 *   <li>{@code path} — request URI that produced the error
 *   <li>{@code details} — optional structured payload with context-specific fields. Always an
 *       object (never null, may be empty {@code {}}). Frontend types this as {@code Record<string,
 *       unknown>}.
 * </ul>
 */
public record ApiError(
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp,
    int status,
    String error,
    String code,
    Object[] args,
    String message,
    String path,
    Map<String, Object> details) {

  /**
   * Compact constructor — normalises {@code details}: null becomes an empty unmodifiable map and
   * any supplied map is wrapped to prevent post-construction mutation.
   */
  public ApiError {
    details =
        details != null
            ? Collections.unmodifiableMap(new LinkedHashMap<>(details))
            : Collections.emptyMap();
    args = args != null ? args.clone() : new Object[0];
  }

  /** Create an error response without extra details (no args). */
  public static ApiError of(int status, String error, String code, String message, String path) {
    return new ApiError(Instant.now(), status, error, code, new Object[0], message, path, null);
  }

  /** Create an error response with dynamic args for parameterized frontend messages. */
  public static ApiError of(
      int status, String error, String code, Object[] args, String message, String path) {
    return new ApiError(Instant.now(), status, error, code, args, message, path, null);
  }

  /** Create an error response with a structured details payload (no args). */
  @SuppressWarnings("unchecked")
  public static ApiError of(
      int status, String error, String code, String message, String path, Map<String, ?> details) {
    return new ApiError(
        Instant.now(),
        status,
        error,
        code,
        new Object[0],
        message,
        path,
        (Map<String, Object>) details);
  }
}
