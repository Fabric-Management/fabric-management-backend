package com.fabricmanagement.approval.domain;

/**
 * Kullanıcı Güven Seviyesi (Trust Level). Yeni kullanıcılar PROBATION ile başlar. Onaylı başarılı
 * işlemlere göre seviye atlarlar.
 */
public enum UserTrustLevel {
  PROBATION,
  STANDARD,
  TRUSTED
}
