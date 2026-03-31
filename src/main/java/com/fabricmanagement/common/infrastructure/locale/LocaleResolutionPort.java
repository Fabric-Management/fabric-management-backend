package com.fabricmanagement.common.infrastructure.locale;

import java.util.Optional;
import java.util.UUID;

/**
 * Locale and timezone resolution port for cross-cutting infrastructure services. Defends Rule 13.3
 * (Cross-Module Infra Isolation) by severing direct dependency from LocalizationService to
 * notification infra.
 */
public interface LocaleResolutionPort {

  /** Returns the user's specific locale override, if any. */
  Optional<String> findUserLocale(UUID userId);

  /** Returns the user's specific timezone override, if any. */
  Optional<String> findUserTimezone(UUID userId);

  /** Returns the tenant's default locale, if any. */
  Optional<String> findTenantDefaultLocale(UUID tenantId);

  /** Returns the tenant's default timezone, if any. */
  Optional<String> findTenantTimezone(UUID tenantId);
}
