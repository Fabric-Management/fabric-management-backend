package com.fabricmanagement.notification.i18n.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Belirli bir locale için çeviri değeri. (translationKeyId, locale, tenantId) üçlüsü benzersizdir.
 * isOverride=true ise tenant kendi çevirisini override etmiştir.
 */
@Entity
@Table(
    schema = "i18n",
    name = "translation_value",
    uniqueConstraints =
        @UniqueConstraint(columnNames = {"translation_key_id", "locale", "tenant_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TranslationValue extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "translation_key_id", nullable = false)
  private TranslationKey translationKey;

  @Column(nullable = false, length = 10)
  private String locale; // EN, TR, DE, FR, AR

  @Column(nullable = false, columnDefinition = "TEXT")
  private String value;

  @Column(nullable = false)
  private Boolean isOverride = false;

  @Override
  protected String getModuleCode() {
    return "TVAL";
  }

  public static TranslationValue of(
      TranslationKey key, String locale, String value, boolean isOverride) {
    var tv = new TranslationValue();
    tv.translationKey = key;
    tv.locale = locale.toUpperCase();
    tv.value = value;
    tv.isOverride = isOverride;
    return tv;
  }
}
