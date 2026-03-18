package com.fabricmanagement.notification.hub.domain;

/** Bildirim gönderim kanalları. */
public enum NotificationChannel {
  /** WebSocket üzerinden uygulama içi bildirim. */
  IN_APP,

  /** E-posta (anlık veya toplu). */
  EMAIL,

  /** Mobil / masaüstü push bildirimi. */
  PUSH
}
