package com.fabricmanagement.notification.i18n.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Kullanıcı seviyesi lokalizasyon ayarları. Kullanıcı başına tek kayıt. Tenant config'i override
 * eder.
 */
@Entity
@Table(schema = "i18n", name = "user_locale_config")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserLocaleConfig extends BaseEntity {

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @Column(nullable = false, length = 10)
  private String locale =
      com.fabricmanagement.common.domain.LocaleConstants.PLATFORM_DEFAULT_LOCALE;

  @Column(name = "date_format", length = 50)
  private String dateFormat;

  @Column(length = 100)
  private String timezone;

  @Override
  protected String getModuleCode() {
    return "ULCFG";
  }

  public static UserLocaleConfig create(UUID tenantId, UUID userId, String locale) {
    var cfg = new UserLocaleConfig();
    cfg.setTenantId(tenantId);
    cfg.userId = userId;
    cfg.locale = locale;
    return cfg;
  }

  public void updateLocale(String locale, String dateFormat, String timezone) {
    this.locale = locale;
    this.dateFormat = dateFormat;
    this.timezone = timezone;
  }
}
