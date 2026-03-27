package com.fabricmanagement.platform.communication.domain;

/** Physical delivery channel for sending verifications and notifications. */
public enum DeliveryChannel {
  EMAIL,
  SMS,
  WHATSAPP,
  TOTP,
  PUSH_NOTIFICATION
}
