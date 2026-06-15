package com.fabricmanagement.notification.i18n.app;

import com.fabricmanagement.notification.i18n.domain.TranslationKey;
import com.fabricmanagement.notification.i18n.domain.TranslationValue;
import com.fabricmanagement.notification.i18n.infra.repository.TenantLocaleConfigRepository;
import com.fabricmanagement.notification.i18n.infra.repository.TranslationKeyRepository;
import com.fabricmanagement.notification.i18n.infra.repository.TranslationValueRepository;
import com.fabricmanagement.notification.i18n.infra.repository.UserLocaleConfigRepository;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * i18n çeviri servisi.
 *
 * <p>Dil öncelik sırası: Kullanıcı ayarı → Tenant default → Sistem default (EN)
 *
 * <p>{parametre} placeholder'larını destekler. Örn: "İş emri {workOrderNumber} onay bekliyor"
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TranslationService {

  private static final String SYSTEM_DEFAULT_LOCALE = "EN";

  private final TranslationKeyRepository translationKeyRepo;
  private final TranslationValueRepository translationValueRepo;
  private final TenantLocaleConfigRepository tenantLocaleConfigRepo;
  private final UserLocaleConfigRepository userLocaleConfigRepo;

  /**
   * Verilen keyCode için çeviri döndürür.
   *
   * <p>Öncelik: tenant override → sistem çevirisi (istenen locale) → sistem çevirisi (EN) →
   * TranslationKey.defaultValue
   */
  @Transactional(readOnly = true)
  @Cacheable(value = "translations", key = "#tenantId + ':' + #locale + ':' + #keyCode")
  public String translate(UUID tenantId, String locale, String keyCode) {
    Objects.requireNonNull(keyCode, "keyCode must not be null");
    String resolvedLocale = locale != null ? locale.toUpperCase() : SYSTEM_DEFAULT_LOCALE;

    // 1. Tenant override
    var tenantOverride =
        translationValueRepo.findByKeyCodeAndLocaleAndTenant(keyCode, resolvedLocale, tenantId);
    if (tenantOverride.isPresent()) {
      return tenantOverride.get().getValue();
    }

    // 2. Sistem çevirisi (istenen locale)
    var systemTranslation = translationValueRepo.findSystemDefault(keyCode, resolvedLocale);
    if (systemTranslation.isPresent()) {
      return systemTranslation.get().getValue();
    }

    // 3. EN fallback
    if (!SYSTEM_DEFAULT_LOCALE.equals(resolvedLocale)) {
      var enFallback = translationValueRepo.findSystemDefault(keyCode, SYSTEM_DEFAULT_LOCALE);
      if (enFallback.isPresent()) {
        log.debug("i18n fallback to EN for key={} locale={}", keyCode, resolvedLocale);
        return enFallback.get().getValue();
      }
    }

    // 4. TranslationKey.defaultValue
    var key = translationKeyRepo.findByKeyCode(keyCode);
    if (key.isPresent()) {
      log.warn(
          "i18n missing translation — using defaultValue: key={} locale={}",
          keyCode,
          resolvedLocale);
      return key.get().getDefaultValue();
    }

    // 5. Son çare: keyCode döndür
    log.error("i18n key not found at all: {}", keyCode);
    return keyCode;
  }

  /**
   * Çeviriyi şablon parametreleriyle birlikte render eder. Örnek: "İş emri {workOrderNumber} onay
   * bekliyor" → "İş emri WO-001 onay bekliyor"
   *
   * <p><b>Security:</b> Tüm parametre değerleri HTML escape edilir (XSS koruması).
   */
  public String translateAndRender(
      UUID tenantId, String locale, String keyCode, Map<String, String> params) {
    String raw = translate(tenantId, locale, keyCode);
    if (params == null || params.isEmpty()) {
      return raw;
    }
    for (var entry : params.entrySet()) {
      String safeValue = escapeHtml(entry.getValue());
      raw = raw.replace("{" + entry.getKey() + "}", safeValue);
    }
    return raw;
  }

  /** HTML special karakter escape — XSS koruması. */
  private String escapeHtml(String value) {
    if (value == null) return "";
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  /**
   * Kullanıcının tercih ettiği locale'i döndürür. Öncelik: UserLocaleConfig → TenantLocaleConfig →
   * EN
   */
  @Transactional(readOnly = true)
  public String resolveLocaleForUser(UUID tenantId, UUID userId) {
    // 1. Kullanıcı tercihi
    var userConfig = userLocaleConfigRepo.findByUserId(userId);
    if (userConfig.isPresent()) {
      return userConfig.get().getLocale();
    }

    // 2. Tenant default
    var tenantConfig = tenantLocaleConfigRepo.findByTenantId(tenantId);
    if (tenantConfig.isPresent()) {
      return tenantConfig.get().getDefaultLocale();
    }

    // 3. Sistem default
    return SYSTEM_DEFAULT_LOCALE;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getUserLocaleConfig(UUID userId) {
    return userLocaleConfigRepo
        .findByUserId(userId)
        .map(
            cfg ->
                Map.<String, Object>of(
                    "locale", cfg.getLocale(),
                    "dateFormat", cfg.getDateFormat() != null ? cfg.getDateFormat() : "",
                    "timezone", cfg.getTimezone() != null ? cfg.getTimezone() : ""))
        .orElse(Map.of());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getTenantLocaleConfig(UUID tenantId) {
    return tenantLocaleConfigRepo
        .findByTenantId(tenantId)
        .map(
            cfg ->
                Map.<String, Object>of(
                    "defaultLocale", cfg.getDefaultLocale(),
                    "supportedLocales", cfg.getSupportedLocales(),
                    "dateFormat", cfg.getDateFormat(),
                    "timeFormat", cfg.getTimeFormat(),
                    "timezone", cfg.getTimezone(),
                    "currency", cfg.getCurrency()))
        .orElse(
            Map.of(
                "defaultLocale",
                com.fabricmanagement.common.domain.LocaleConstants.PLATFORM_DEFAULT_LOCALE,
                "supportedLocales",
                com.fabricmanagement.common.domain.LocaleConstants
                    .PLATFORM_DEFAULT_SUPPORTED_LOCALES));
  }

  @Transactional
  public void updateUserLocale(
      UUID tenantId, UUID userId, String locale, String dateFormat, String timezone) {
    userLocaleConfigRepo
        .findByUserId(userId)
        .ifPresentOrElse(
            cfg -> {
              cfg.updateLocale(locale, dateFormat, timezone);
              userLocaleConfigRepo.save(cfg);
            },
            () -> {
              var cfg =
                  com.fabricmanagement.notification.i18n.domain.UserLocaleConfig.create(
                      tenantId, userId, locale);
              cfg.updateLocale(locale, dateFormat, timezone);
              userLocaleConfigRepo.save(cfg);
            });
  }

  /** Yeni bir çeviri anahtarı kaydeder (idempotent — aynı keyCode varsa atla). */
  @Transactional
  public TranslationKey registerKey(
      String keyCode, String module, String defaultValue, String description) {
    return translationKeyRepo
        .findByKeyCode(keyCode)
        .orElseGet(
            () -> {
              var key = TranslationKey.of(keyCode, module, defaultValue, description);
              return translationKeyRepo.save(key);
            });
  }

  /** Belirtilen key + locale için sistem çevirisi ekler (idempotent). */
  @Transactional
  public TranslationValue addSystemTranslation(String keyCode, String locale, String value) {
    var key =
        translationKeyRepo
            .findByKeyCode(keyCode)
            .orElseThrow(
                () -> new IllegalArgumentException("TranslationKey not found: " + keyCode));

    var existing = translationValueRepo.findSystemDefault(keyCode, locale.toUpperCase());
    if (existing.isPresent()) {
      return existing.get();
    }

    var tv = TranslationValue.of(key, locale, value, false);
    return translationValueRepo.save(tv);
  }
}
