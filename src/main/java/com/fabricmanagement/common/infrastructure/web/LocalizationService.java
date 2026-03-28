package com.fabricmanagement.common.infrastructure.web;

import com.fabricmanagement.notification.i18n.infra.repository.TenantLocaleConfigRepository;
import com.fabricmanagement.notification.i18n.infra.repository.UserLocaleConfigRepository;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central localization utility for backend-generated text (emails, scheduler notifications).
 *
 * <h2>Two text sources</h2>
 *
 * <ol>
 *   <li><b>MessageSource (properties)</b> — for structural backend messages like email templates.
 *       Source: {@code src/main/resources/i18n/messages*.properties}
 *   <li><b>TranslationService (DB)</b> — for dynamic notification content managed at runtime. Used
 *       indirectly via {@code notification/i18n} module.
 * </ol>
 *
 * <h2>Cascade order (locale resolution)</h2>
 *
 * <ol>
 *   <li>User's preferred locale ({@code User.preferredLocale} → {@code user_locale_config})
 *   <li>Tenant default locale ({@code TenantSettings.locale} → {@code tenant_locale_config})
 *   <li>System default — English
 * </ol>
 *
 * <h2>Usage example</h2>
 *
 * <pre>{@code
 * // In a scheduler job or event listener:
 * Locale locale = localizationService.resolveLocaleForUser(tenantId, userId);
 * String subject = localizationService.getMessage("email.approval.approved.subject", null, locale);
 * }</pre>
 */
@org.springframework.stereotype.Component
@RequiredArgsConstructor
@Slf4j
public class LocalizationService {

  private static final Locale FALLBACK_LOCALE = Locale.ENGLISH;
  private static final ZoneId FALLBACK_TIMEZONE = ZoneId.of("UTC");

  private final MessageSource messageSource;
  private final UserLocaleConfigRepository userLocaleConfigRepository;
  private final TenantLocaleConfigRepository tenantLocaleConfigRepository;

  // ============================================================
  // MESSAGE RESOLUTION (properties-based)
  // ============================================================

  /**
   * Resolves a message key from the properties files for the given locale.
   *
   * <p>Falls back to English if the key is missing in the requested locale. Returns the key code
   * itself if the key does not exist at all (no exception thrown — fail-safe).
   *
   * @param code message key (e.g. {@code "email.approval.approved.subject"})
   * @param args optional positional arguments for {@link java.text.MessageFormat} placeholders
   * @param locale target locale
   * @return resolved message string
   */
  public String getMessage(String code, Object[] args, Locale locale) {
    try {
      return messageSource.getMessage(code, args, locale != null ? locale : FALLBACK_LOCALE);
    } catch (org.springframework.context.NoSuchMessageException e) {
      log.warn("i18n message key not found: '{}' for locale '{}'", code, locale);
      return code; // Fail-safe: return key code so nothing crashes
    }
  }

  /**
   * Convenience overload — uses the current request's locale from {@link LocalizationContext}.
   *
   * @param code message key
   * @param args optional arguments
   * @return resolved message string
   */
  public String getMessage(String code, Object[] args) {
    return getMessage(code, args, Locale.forLanguageTag(LocalizationContext.getLocale()));
  }

  // ============================================================
  // LOCALE / TIMEZONE CASCADE RESOLUTION (DB-based)
  // ============================================================

  /**
   * Resolves the effective locale for a user using the 3-tier cascade:
   *
   * <ol>
   *   <li>User locale config ({@code i18n.user_locale_config.locale})
   *   <li>Tenant locale config ({@code i18n.tenant_locale_config.default_locale})
   *   <li>System fallback — English
   * </ol>
   *
   * @param tenantId tenant identifier
   * @param userId user identifier
   * @return resolved {@link Locale} (never null)
   */
  @Transactional(readOnly = true)
  public Locale resolveLocaleForUser(UUID tenantId, UUID userId) {
    // 1. User preference
    var userConfig = userLocaleConfigRepository.findByUserId(userId);
    if (userConfig.isPresent() && userConfig.get().getLocale() != null) {
      return parseLocale(userConfig.get().getLocale());
    }

    // 2. Tenant default
    var tenantConfig = tenantLocaleConfigRepository.findByTenantId(tenantId);
    if (tenantConfig.isPresent() && tenantConfig.get().getDefaultLocale() != null) {
      return parseLocale(tenantConfig.get().getDefaultLocale());
    }

    // 3. System fallback
    log.debug("resolveLocaleForUser: no config found for userId={} — using EN fallback", userId);
    return FALLBACK_LOCALE;
  }

  /**
   * Resolves the effective locale for a tenant (for scheduler jobs without a user context).
   *
   * <ol>
   *   <li>Tenant locale config ({@code i18n.tenant_locale_config.default_locale})
   *   <li>System fallback — English
   * </ol>
   *
   * @param tenantId tenant identifier
   * @return resolved {@link Locale} (never null)
   */
  @Transactional(readOnly = true)
  public Locale resolveLocaleForTenant(UUID tenantId) {
    var tenantConfig = tenantLocaleConfigRepository.findByTenantId(tenantId);
    if (tenantConfig.isPresent() && tenantConfig.get().getDefaultLocale() != null) {
      return parseLocale(tenantConfig.get().getDefaultLocale());
    }
    log.debug("resolveLocaleForTenant: no config for tenantId={} — using EN fallback", tenantId);
    return FALLBACK_LOCALE;
  }

  /**
   * Resolves the effective timezone for a user using the 3-tier cascade:
   *
   * <ol>
   *   <li>User locale config ({@code i18n.user_locale_config.timezone})
   *   <li>Tenant locale config ({@code i18n.tenant_locale_config.timezone})
   *   <li>System fallback — UTC
   * </ol>
   *
   * @param tenantId tenant identifier
   * @param userId user identifier
   * @return resolved {@link ZoneId} (never null, defaults to UTC)
   */
  @Transactional(readOnly = true)
  public ZoneId resolveTimezoneForUser(UUID tenantId, UUID userId) {
    // 1. User preference
    var userConfig = userLocaleConfigRepository.findByUserId(userId);
    if (userConfig.isPresent() && userConfig.get().getTimezone() != null) {
      return parseZoneId(userConfig.get().getTimezone());
    }

    // 2. Tenant default
    var tenantConfig = tenantLocaleConfigRepository.findByTenantId(tenantId);
    if (tenantConfig.isPresent() && tenantConfig.get().getTimezone() != null) {
      return parseZoneId(tenantConfig.get().getTimezone());
    }

    // 3. System fallback
    log.debug(
        "resolveTimezoneForUser: no timezone config for userId={}/tenantId={} — using UTC fallback",
        userId,
        tenantId);
    return FALLBACK_TIMEZONE;
  }

  // ============================================================
  // PRIVATE HELPERS
  // ============================================================

  private static Locale parseLocale(String code) {
    try {
      return Locale.forLanguageTag(code.replace("_", "-"));
    } catch (Exception e) {
      log.warn("Invalid locale code '{}', falling back to EN", code);
      return FALLBACK_LOCALE;
    }
  }

  private static ZoneId parseZoneId(String zoneId) {
    try {
      return ZoneId.of(zoneId);
    } catch (Exception e) {
      log.warn("Invalid ZoneId '{}', falling back to UTC", zoneId);
      return FALLBACK_TIMEZONE;
    }
  }
}
