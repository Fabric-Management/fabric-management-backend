package com.fabricmanagement.common.infrastructure.web.exception;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Abstract base for all domain rule violations. Subclasses declare their errorCode and HTTP status.
 */
public abstract class DomainException extends RuntimeException {

  private final String errorCode;
  private final int httpStatus;
  private final Map<String, Object> details;

  /**
   * Field errors by request path, written as the problem's typed {@code errors} map, like bean
   * validation failures. Never a detail: that would write a second {@code errors} key.
   */
  private final Map<String, String> fieldErrors = new LinkedHashMap<>();

  /** Names the problem body already has as typed fields; a detail may not take them. */
  static final Set<String> RESERVED_DETAILS =
      Set.of("type", "title", "status", "detail", "instance", "code", "errors", "args", "traceId");

  /**
   * Dynamic arguments for parameterized error messages.
   *
   * <p>Frontend can use these to build localized messages: <code>
   * t(error.code, { min: error.args[0] })</code>
   *
   * <p>Example: {@code new DomainException(msg, "ERROR_MIN_VALUE", 400, new Object[]{8})}
   */
  private final Object[] args;

  protected DomainException(String message, String errorCode, int httpStatus) {
    super(message);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
    this.details = new HashMap<>();
    this.args = new Object[0];
  }

  protected DomainException(String message, String errorCode, int httpStatus, Object[] args) {
    super(message);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
    this.details = new HashMap<>();
    this.args = args != null ? args.clone() : new Object[0];
  }

  protected DomainException(String message, String errorCode, int httpStatus, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
    this.details = new HashMap<>();
    this.args = new Object[0];
  }

  @SuppressWarnings("unchecked")
  public <T extends DomainException> T withDetail(String key, Object value) {
    if (RESERVED_DETAILS.contains(key)) {
      throw new IllegalArgumentException("A problem detail cannot be named " + key);
    }
    this.details.put(key, value);
    return (T) this;
  }

  /** Adds a field error by its request path (for example {@code header.notes.value}). */
  @SuppressWarnings("unchecked")
  public <T extends DomainException> T withFieldError(String path, String message) {
    this.fieldErrors.put(path, message == null ? "" : message);
    return (T) this;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public int getHttpStatus() {
    return httpStatus;
  }

  /** Dynamic arguments for parameterized frontend messages. Never null (may be empty). */
  public Object[] getArgs() {
    return args.clone();
  }

  public Map<String, Object> getDetails() {
    return Collections.unmodifiableMap(details);
  }

  /** Field errors by request path; empty when the failure is not about request fields. */
  public Map<String, String> getFieldErrors() {
    return Collections.unmodifiableMap(fieldErrors);
  }
}
