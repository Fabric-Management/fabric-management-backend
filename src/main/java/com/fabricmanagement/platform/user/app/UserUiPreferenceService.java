package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.UidSuffix;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.domain.UserUiPreference;
import com.fabricmanagement.platform.user.dto.UiPreferenceResponse;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.platform.user.infra.repository.UserUiPreferenceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interface preferences of a user: ordered identifiers under keys the screens own (for example the
 * order of a page's sections). The values are kept as given and never interpreted here; the screen
 * falls back to its default for identifiers it does not know. Stays within the user module; no
 * facade.
 *
 * <p>All calls must be tenant-scoped: pass {@code TenantContext.requireTenantId()} as {@code
 * tenantId}.
 */
@Service
@RequiredArgsConstructor
public class UserUiPreferenceService {

  /** Two to five dot-separated lowercase segments, e.g. {@code sales.new-order.sections}. */
  static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*){1,4}$");

  static final int MAX_KEY_LENGTH = 100;
  static final int MAX_VALUES = 50;
  static final int MAX_VALUE_LENGTH = 100;

  private final UserUiPreferenceRepository preferenceRepository;
  private final UserRepository userRepository;
  private final ObjectMapper objectMapper;
  private final AuditorAware<UUID> auditorProvider;

  /**
   * The user's preference under the key; empty values when it was never saved. Never 404s for a
   * missing preference.
   */
  @Transactional(readOnly = true)
  public UiPreferenceResponse getPreference(UUID tenantId, UUID userId, String key) {
    requireArguments(tenantId, userId);
    requireKey(key);
    return preferenceRepository
        .findByTenantIdAndUser_IdAndPreferenceKey(tenantId, userId, key)
        .map(this::toResponse)
        .orElseGet(() -> new UiPreferenceResponse(key, List.of()));
  }

  /** Replaces the values under the key, creating the preference when it does not exist yet. */
  @Transactional
  public UiPreferenceResponse savePreference(
      UUID tenantId, UUID userId, String key, List<String> values) {
    requireArguments(tenantId, userId);
    requireKey(key);
    requireValues(values);
    userRepository
        .findByTenantIdAndId(tenantId, userId)
        .orElseThrow(() -> new NotFoundException("User not found: " + userId));

    // Match BaseEntity.generateUid/onCreate and the configured JPA auditor. Native SQL bypasses
    // those callbacks, so every identifier, audit, soft-delete and version column is explicit.
    String tenantUid = TenantContext.getCurrentTenantUid();
    String uid = (tenantUid == null ? "SYS-000" : tenantUid) + "-UIPREF-" + UidSuffix.random();
    UserUiPreference saved =
        preferenceRepository.upsert(
            tenantId,
            userId,
            key,
            UUID.randomUUID(),
            uid,
            objectMapper.valueToTree(values).toString(),
            Instant.now(),
            auditorProvider.getCurrentAuditor().orElse(null));
    return toResponse(saved);
  }

  private UiPreferenceResponse toResponse(UserUiPreference preference) {
    List<String> values = preference.getPreferenceValues();
    return new UiPreferenceResponse(
        preference.getPreferenceKey(), values == null ? List.of() : List.copyOf(values));
  }

  private static void requireArguments(UUID tenantId, UUID userId) {
    if (tenantId == null || userId == null) {
      throw new PlatformDomainException(
          "tenantId and userId must not be null", "USER_PREF_INVALID_ARGS", 400);
    }
  }

  private static void requireKey(String key) {
    if (key == null || key.length() > MAX_KEY_LENGTH || !KEY_PATTERN.matcher(key).matches()) {
      throw new PlatformDomainException(
          "Preference key must be 2-5 dot-separated lowercase segments of at most "
              + MAX_KEY_LENGTH
              + " characters",
          "USER_UI_PREF_INVALID_KEY",
          400);
    }
  }

  private static void requireValues(List<String> values) {
    if (values == null || values.size() > MAX_VALUES) {
      throw new PlatformDomainException(
          "Preference values must be a list of at most " + MAX_VALUES + " identifiers",
          "USER_UI_PREF_INVALID_VALUES",
          400);
    }
    var seen = new HashSet<String>();
    for (String value : values) {
      if (value == null || value.isBlank() || value.length() > MAX_VALUE_LENGTH) {
        throw new PlatformDomainException(
            "Each preference value must be 1-" + MAX_VALUE_LENGTH + " characters",
            "USER_UI_PREF_INVALID_VALUES",
            400);
      }
      if (!seen.add(value)) {
        throw new PlatformDomainException(
            "Preference values must be unique: " + value, "USER_UI_PREF_INVALID_VALUES", 400);
      }
    }
  }
}
