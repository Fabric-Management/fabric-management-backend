package com.fabricmanagement.notification.i18n.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Çeviri anahtarı — modül + keyCode kombinasyonu sisteme özel benzersizdir. defaultValue EN
 * fallback'tir. Tüm modüller bu tabloya kayıt ekler.
 */
@Entity
@Table(
    schema = "i18n",
    name = "translation_key",
    uniqueConstraints = @UniqueConstraint(columnNames = {"key_code"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TranslationKey extends BaseEntity {

  @Column(name = "key_code", nullable = false, length = 255)
  private String keyCode; // notification.work_order_pending_approval.title

  @Column(nullable = false, length = 50)
  private String module; // NOTIFICATION, PRODUCTION, ORDER

  @Column(name = "default_value", nullable = false, columnDefinition = "TEXT")
  private String defaultValue; // EN fallback metni

  @Column(columnDefinition = "TEXT")
  private String description;

  @Override
  protected String getModuleCode() {
    return "TKEY";
  }

  public static TranslationKey of(
      String keyCode, String module, String defaultValue, String description) {
    var key = new TranslationKey();
    key.keyCode = keyCode;
    key.module = module;
    key.defaultValue = defaultValue;
    key.description = description;
    return key;
  }
}
