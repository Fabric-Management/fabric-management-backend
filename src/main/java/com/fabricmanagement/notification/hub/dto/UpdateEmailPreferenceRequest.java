package com.fabricmanagement.notification.hub.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Whether a notification is also sent by e-mail. {@code Boolean}, not {@code boolean}: a body that
 * leaves the field out must be rejected, not read as "off".
 */
public record UpdateEmailPreferenceRequest(@NotNull Boolean email) {}
