package com.fabricmanagement.common.infrastructure.web.exception;

/**
 * Thrown when signup/onboarding attempts to create a company with a tax ID that already exists.
 *
 * <p>Clients should check error code {@code TAX_ID_ALREADY_EXISTS} and direct the user to login or
 * password reset.
 */
public class TaxIdAlreadyExistsException extends RuntimeException {

  public TaxIdAlreadyExistsException(String message) {
    super(message);
  }
}
