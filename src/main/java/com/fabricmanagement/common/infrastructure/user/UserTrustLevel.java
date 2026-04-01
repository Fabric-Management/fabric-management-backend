package com.fabricmanagement.common.infrastructure.user;

/**
 * Kullanıcı Güven Seviyesi (Trust Level). Yeni kullanıcılar PROBATION ile başlar. Onaylı başarılı
 * işlemlere göre seviye atlarlar.
 */
public enum UserTrustLevel {
  PROBATION,
  STANDARD,
  TRUSTED
}
