package com.fabricmanagement.notification.i18n.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Sistemde desteklenen dil seçeneklerini tanımlar. Sistem tarafından yönetilir, tenant edemez
 * ekleyemez.
 */
@Entity
@Table(schema = "i18n", name = "supported_locale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportedLocale extends BaseEntity {

  @Column(nullable = false, unique = true, length = 10)
  private String code; // EN, TR, DE, FR, AR

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false)
  private Boolean isRtl = false;

  @Override
  protected String getModuleCode() {
    return "LOCALE";
  }

  public static SupportedLocale of(String code, String name, boolean isRtl) {
    var locale = new SupportedLocale();
    locale.code = code.toUpperCase();
    locale.name = name;
    locale.isRtl = isRtl;
    return locale;
  }
}
