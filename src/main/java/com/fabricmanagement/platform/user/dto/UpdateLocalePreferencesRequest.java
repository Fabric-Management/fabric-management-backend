package com.fabricmanagement.platform.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request for updating the authenticated user's own locale and timezone preferences.
 *
 * <p>Either field can be null to clear the user-level override and fall back to the tenant setting.
 */
public record UpdateLocalePreferencesRequest(

    /**
     * IETF BCP 47 language tag (e.g. "en-US", "tr-TR"). Null clears the user override so the tenant
     * default is used instead.
     */
    @Pattern(
            regexp = "^[a-zA-Z]{2}(-[a-zA-Z]{2,4})?$",
            message = "Locale must be a valid BCP 47 tag (e.g. en-US, tr-TR)")
        String preferredLocale,

    /**
     * IANA timezone identifier (e.g. "Europe/Istanbul", "UTC"). Null clears the user override so
     * the tenant default is used instead.
     */
    @Size(max = 50, message = "Timezone identifier must be at most 50 characters")
        String preferredTimezone) {}
