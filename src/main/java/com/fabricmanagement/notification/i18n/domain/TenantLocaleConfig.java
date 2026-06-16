package com.fabricmanagement.notification.i18n.domain;

import com.fabricmanagement.common.domain.CurrencyConstants;
import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

/** Tenant seviyesi lokalizasyon ayarları. Tenant başına tek kayıt (tenant_id UNIQUE). */
@Entity
@Table(schema = "i18n", name = "tenant_locale_config")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantLocaleConfig extends BaseEntity {

  @Column(name = "default_locale", nullable = false, length = 10)
  private String defaultLocale =
      com.fabricmanagement.common.domain.LocaleConstants.PLATFORM_DEFAULT_LOCALE;

  @Type(JsonBinaryType.class)
  @Column(name = "supported_locales", columnDefinition = "JSONB")
  private List<String> supportedLocales =
      com.fabricmanagement.common.domain.LocaleConstants.PLATFORM_DEFAULT_SUPPORTED_LOCALES;

  @Column(name = "date_format", nullable = false, length = 50)
  private String dateFormat = "dd.MM.yyyy";

  @Column(name = "time_format", nullable = false, length = 50)
  private String timeFormat = "HH:mm";

  @Column(nullable = false, length = 100)
  private String timezone =
      com.fabricmanagement.common.domain.LocaleConstants.PLATFORM_DEFAULT_TIMEZONE;

  @Column(nullable = false, length = 10)
  private String currency = CurrencyConstants.PLATFORM_DEFAULT_CURRENCY;

  @Override
  protected String getModuleCode() {
    return "TLCFG";
  }

  public static TenantLocaleConfig createDefault(UUID tenantId) {
    var config = new TenantLocaleConfig();
    config.setTenantId(tenantId);
    return config;
  }

  public void update(
      String defaultLocale,
      List<String> supportedLocales,
      String dateFormat,
      String timeFormat,
      String timezone,
      String currency) {
    this.defaultLocale = defaultLocale;
    this.supportedLocales = supportedLocales;
    this.dateFormat = dateFormat;
    this.timeFormat = timeFormat;
    this.timezone = timezone;
    this.currency = currency;
  }
}
