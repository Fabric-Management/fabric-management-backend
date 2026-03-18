package com.fabricmanagement.notification.hub.domain;

/** Bildirim gönderim tipi. */
public enum NotificationDeliveryType {
  /** Anında gönder — CRITICAL eventler için. */
  INSTANT,

  /** Belirli bir zamanda gönder — scheduler'la. */
  SCHEDULED,

  /** Grupla ve özet gönder — NORMAL eventler. */
  DIGEST
}
