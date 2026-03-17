package com.fabricmanagement.notification.hub.domain;

/** Bildirim önem seviyeleri. */
public enum NotificationImportance {

  /** Anında gönderilir, tüm kanallar, kullanıcı tercihi yok sayılır. */
  CRITICAL,

  /** 5 dakika gruplama, tüm aktif kanallar. */
  HIGH,

  /** 5 dakika gruplama, kullanıcı tercihine göre kanal. */
  NORMAL
}
