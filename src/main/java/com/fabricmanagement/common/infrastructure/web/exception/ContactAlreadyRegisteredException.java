package com.fabricmanagement.common.infrastructure.web.exception;

/**
 * Thrown when signup/onboarding attempts to use a contact value (email/phone) that is already
 * registered to a user.
 *
 * <p>Clients should check error code {@code CONTACT_ALREADY_REGISTERED} and direct the user to
 * login or use a different contact.
 */
public class ContactAlreadyRegisteredException extends RuntimeException {

  public ContactAlreadyRegisteredException(String message) {
    super(message);
  }
}
