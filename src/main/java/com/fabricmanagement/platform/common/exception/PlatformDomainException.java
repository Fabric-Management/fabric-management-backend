package com.fabricmanagement.platform.common.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/**
 * Base exception for all Platform domain rule violations.
 *
 * <p>Use directly for generic platform errors, or subclass for more specific contexts:
 *
 * <ul>
 *   <li>{@code AuthDomainException} — authentication and token errors
 *   <li>{@code UserDomainException} — user management errors
 *   <li>{@code OrganizationDomainException} — organization errors
 * </ul>
 */
public class PlatformDomainException extends DomainException {

  public PlatformDomainException(String message) {
    super(message, "PLATFORM_RULE_VIOLATION", 400);
  }

  public PlatformDomainException(String message, Throwable cause) {
    super(message, "PLATFORM_RULE_VIOLATION", 400, cause);
  }

  public PlatformDomainException(String message, String errorCode, int httpStatus) {
    super(message, errorCode, httpStatus);
  }

  public PlatformDomainException(String message, String errorCode, int httpStatus, Object[] args) {
    super(message, errorCode, httpStatus, args);
  }
}
