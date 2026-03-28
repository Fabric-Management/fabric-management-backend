package com.fabricmanagement.platform.communication.domain;

/**
 * Delivery channel for user notifications.
 *
 * <p>Determines how a notification is delivered: in-app only, email only, or both.
 *
 * <p>Note: Named NotificationDeliveryChannel to avoid conflict with existing NotificationChannel
 * (WHATSAPP, EMAIL, SMS) used for verification/delivery routing.
 */
public enum NotificationDeliveryChannel {

  /** In-app notification only */
  IN_APP,

  /** Email only */
  EMAIL,

  /** Both in-app and email */
  BOTH
}
