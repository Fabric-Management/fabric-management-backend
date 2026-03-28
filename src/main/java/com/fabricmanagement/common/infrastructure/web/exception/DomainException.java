package com.fabricmanagement.common.infrastructure.web.exception;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base for all domain rule violations. Subclasses declare their errorCode and HTTP status.
 */
public abstract class DomainException extends RuntimeException {

  private final String errorCode;
  private final int httpStatus;
  private final Map<String, Object> details;

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
    this.details.put(key, value);
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
}
