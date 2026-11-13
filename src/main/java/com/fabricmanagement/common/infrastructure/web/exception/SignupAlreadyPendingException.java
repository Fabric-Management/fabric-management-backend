package com.fabricmanagement.common.infrastructure.web.exception;

/**
 * Thrown when a signup uses a contact value (e-mail) for which another signup is still waiting to
 * be completed: its setup link has not been used and has not expired.
 *
 * <p>Clients should check error code {@code SIGNUP_ALREADY_PENDING} and tell the person to use the
 * link already sent to that address, since there is no account to sign in to yet.
 */
public class SignupAlreadyPendingException extends RuntimeException {

  public SignupAlreadyPendingException(String message) {
    super(message);
  }
}
