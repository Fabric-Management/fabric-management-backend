package com.fabricmanagement.notification.common.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/** Bildirim modülü temel exception sınıfı. */
public class NotificationDomainException extends DomainException {

  public NotificationDomainException(String message) {
    super(message, "NOTIFICATION_ERROR", 422);
  }

  public NotificationDomainException(String message, Throwable cause) {
    super(message, "NOTIFICATION_ERROR", 422, cause);
  }
}
