package com.fabricmanagement.common.infrastructure.web.exception;

/** Thrown when rate limit is exceeded (e.g. contact enumeration protection). */
public class TooManyRequestsException extends RuntimeException {

  public TooManyRequestsException(String message) {
    super(message);
  }
}
