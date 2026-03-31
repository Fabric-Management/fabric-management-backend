package com.fabricmanagement.platform.user.domain.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Port used by UserLocaleService to safely persist and retrieve cross-module locale preferences in
 * the notification module without depending on its infrastructure.
 */
public interface UserLocaleConfigPort {

  Optional<UserLocalePreferences> findByUserId(UUID userId);

  void saveOrUpdate(UUID tenantId, UUID userId, String locale, String timezone);

  void deleteByUserId(UUID userId);
}
