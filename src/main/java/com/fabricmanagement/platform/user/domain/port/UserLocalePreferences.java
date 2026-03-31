package com.fabricmanagement.platform.user.domain.port;

import java.util.UUID;

/**
 * Platform-side definition of User Locale Preferences. Created to prevent platform module coupling
 * to the Notification module's UserLocaleConfig entity.
 */
public record UserLocalePreferences(UUID userId, String locale, String timezone) {}
