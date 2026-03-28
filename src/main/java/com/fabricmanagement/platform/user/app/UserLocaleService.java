package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.notification.i18n.domain.UserLocaleConfig;
import com.fabricmanagement.notification.i18n.infra.repository.UserLocaleConfigRepository;
import com.fabricmanagement.platform.user.dto.UpdateLocalePreferencesRequest;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing user locale and timezone preferences.
 *
 * <p>Maintains two sources of truth in sync:
 *
 * <ol>
 *   <li>{@code common_user.preferred_locale / preferred_timezone} — fast, available in UserDto
 *   <li>{@code i18n.user_locale_config} — used by {@link
 *       com.fabricmanagement.notification.i18n.app.TranslationService} for cascade resolution
 * </ol>
 *
 * <p>Cascade order: User → Tenant settings → System default (EN)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserLocaleService {

  private final UserRepository userRepository;
  private final UserQueryService userQueryService;
  private final UserLocaleConfigRepository userLocaleConfigRepository;

  /**
   * Update the authenticated user's own locale and timezone preferences (self-service).
   *
   * @param userId ID of the user performing the update (self-update)
   * @param request new locale/timezone preferences (null values clear the override)
   * @return updated {@link UserDto}
   */
  @Transactional
  public UserDto updateLocalePreferences(UUID userId, UpdateLocalePreferencesRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    var user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

    user.updateLocalePreferences(request.preferredLocale(), request.preferredTimezone());
    var saved = userRepository.save(user);

    // Sync with the i18n module's UserLocaleConfig so TranslationService cascade works correctly
    syncUserLocaleConfig(tenantId, userId, request.preferredLocale(), request.preferredTimezone());

    log.info(
        "Locale preferences updated: userId={}, locale={}, timezone={}",
        userId,
        request.preferredLocale(),
        request.preferredTimezone());

    return userQueryService.findById(tenantId, saved.getId()).orElseThrow();
  }

  // ============================================================
  // PRIVATE HELPERS
  // ============================================================

  /**
   * Keeps {@code i18n.user_locale_config} in sync with the user-level locale update.
   *
   * <p>If a {@link UserLocaleConfig} already exists for this user it is updated; otherwise a new
   * one is created. If both locale and timezone are null (clearing the override) the config row is
   * removed so the cascade falls through to tenant settings.
   */
  private void syncUserLocaleConfig(UUID tenantId, UUID userId, String locale, String timezone) {

    if (locale == null && timezone == null) {
      // Clear override: remove the config row so cascade reaches tenant settings
      userLocaleConfigRepository.findByUserId(userId).ifPresent(userLocaleConfigRepository::delete);
      return;
    }

    var cfg =
        userLocaleConfigRepository
            .findByUserId(userId)
            .orElseGet(
                () -> UserLocaleConfig.create(tenantId, userId, locale != null ? locale : "EN"));

    // Normalize locale to uppercase 2-char code for i18n module (it uses e.g. "TR", "EN")
    String normalizedLocale = locale != null ? extractLanguageCode(locale) : cfg.getLocale();
    cfg.updateLocale(normalizedLocale, null, timezone);
    userLocaleConfigRepository.save(cfg);
  }

  /** Extracts the primary language subtag from a BCP 47 tag (e.g. "tr-TR" → "TR"). */
  private static String extractLanguageCode(String bcp47Tag) {
    if (bcp47Tag == null) return "EN";
    String lang = bcp47Tag.split("[-_]")[0].toUpperCase();
    return lang.isBlank() ? "EN" : lang;
  }
}
