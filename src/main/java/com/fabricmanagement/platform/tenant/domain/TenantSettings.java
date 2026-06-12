package com.fabricmanagement.platform.tenant.domain;

import com.fabricmanagement.common.domain.CurrencyConstants;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tenant-level settings stored as JSONB.
 *
 * <p>Contains platform-wide configuration for a tenant:
 *
 * <ul>
 *   <li>Localization (timezone, locale, currency)
 *   <li>Feature toggles (beta features, A/B tests)
 *   <li>Branding (logo URL, primary color)
 *   <li>Notification preferences
 * </ul>
 *
 * <h2>Usage:</h2>
 *
 * <pre>{@code
 * Tenant tenant = tenantRepository.findById(tenantId);
 * String timezone = tenant.getSettings().getTimezone(); // "Europe/London"
 * boolean betaEnabled = tenant.getSettings().isBetaFeaturesEnabled();
 * }</pre>
 *
 * <h2>Database Storage:</h2>
 *
 * <p>Stored as JSONB in {@code common_tenant.settings} column. Use Hibernate's
 * {@code @JdbcTypeCode(SqlTypes.JSON)} for automatic serialization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TenantSettings implements Serializable {

  private static final long serialVersionUID = 1L;

  // ========================================
  // LOCALIZATION
  // ========================================

  /** Timezone for date/time display (IANA format). Default: Europe/London */
  @Builder.Default private String timezone = "Europe/London";

  /** Locale for number/date formatting. Default: en-GB */
  @Builder.Default private String locale = "en-GB";

  /** Primary currency code (ISO 4217). Default: PLATFORM_DEFAULT_CURRENCY (GBP) */
  @Builder.Default private String currency = CurrencyConstants.PLATFORM_DEFAULT_CURRENCY;

  /** Primary country code (ISO 3166-1 alpha-2). Default: GB */
  @Builder.Default private String country = "GB";

  // ========================================
  // FEATURE TOGGLES
  // ========================================

  /** Enable beta features for this tenant. Default: false */
  @Builder.Default private boolean betaFeaturesEnabled = false;

  /** Enable AI-powered features. Default: true */
  @Builder.Default private boolean aiEnabled = true;

  /** Enable email notifications. Default: true */
  @Builder.Default private boolean emailNotificationsEnabled = true;

  // ========================================
  // BRANDING
  // ========================================

  /** Custom logo URL for white-labeling. */
  private String logoUrl;

  /** Primary brand color (hex, e.g., "#1E88E5"). */
  private String primaryColor;

  // ========================================
  // SECURITY
  // ========================================

  /** Require 2FA for all users. Default: false */
  @Builder.Default private boolean mfaRequired = false;

  /** Session timeout in minutes. Default: 480 (8 hours) */
  @Builder.Default private int sessionTimeoutMinutes = 480;

  /** Allowed IP ranges (CIDR notation). Null = no restriction. */
  private String[] allowedIpRanges;

  // ========================================
  // FACTORY METHODS
  // ========================================

  /**
   * Create default settings for a new tenant.
   *
   * @return settings with defaults
   */
  public static TenantSettings defaults() {
    return TenantSettings.builder().build();
  }
}
