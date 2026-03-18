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

  protected DomainException(String message, String errorCode, int httpStatus) {
    super(message);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
    this.details = new HashMap<>();
  }

  protected DomainException(String message, String errorCode, int httpStatus, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
    this.details = new HashMap<>();
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

  public Map<String, Object> getDetails() {
    return Collections.unmodifiableMap(details);
  }
}
