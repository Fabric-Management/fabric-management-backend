package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.UserNavPreferences;
import com.fabricmanagement.platform.user.dto.NavPreferencesImportResponse;
import com.fabricmanagement.platform.user.dto.NavPreferencesMapper;
import com.fabricmanagement.platform.user.dto.NavPreferencesRequest;
import com.fabricmanagement.platform.user.dto.NavPreferencesResponse;
import com.fabricmanagement.platform.user.infra.repository.UserNavPreferencesRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for user nav preferences. Stays within the user module; no facade.
 *
 * <p>All calls must be tenant-scoped: pass {@code TenantContext.requireTenantId()} as {@code
 * tenantId}.
 */
@Service
@RequiredArgsConstructor
public class UserNavPreferencesService {

  private final UserNavPreferencesRepository preferencesRepository;
  private final UserRepository userRepository;
  private final NavPreferencesMapper navPreferencesMapper;
  private final ObjectMapper objectMapper;
  private final AuditorAware<UUID> auditorProvider;

  /**
   * Import once, preserving any existing row (including deliberately empty preferences). The
   * conflict read is a separate statement so READ COMMITTED sees the winning transaction's commit.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public NavPreferencesImportResponse importPreferences(
      UUID tenantId, UUID userId, NavPreferencesRequest request) {
    if (tenantId == null || userId == null) {
      throw new PlatformDomainException(
          "tenantId and userId must not be null", "USER_PREF_INVALID_ARGS", 400);
    }
    userRepository
        .findByTenantIdAndId(tenantId, userId)
        .orElseThrow(() -> new NotFoundException("User not found: " + userId));

    // Match BaseEntity.generateUid/onCreate and the configured JPA auditor. Native SQL bypasses
    // those callbacks, so every identifier, audit, soft-delete and version column is explicit.
    String tenantUid = TenantContext.getCurrentTenantUid();
    String uid =
        (tenantUid == null ? "SYS-000" : tenantUid)
            + "-NAVPREF-"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    var inserted =
        preferencesRepository.insertIfAbsent(
            tenantId,
            userId,
            UUID.randomUUID(),
            uid,
            objectMapper
                .valueToTree(
                    request.getSortOrder() == null
                        ? NavPreferencesConstants.DEFAULT_SORT_ORDER
                        : request.getSortOrder())
                .toString(),
            objectMapper
                .valueToTree(
                    request.getHiddenItemIds() == null
                        ? NavPreferencesConstants.DEFAULT_HIDDEN_ITEM_IDS
                        : request.getHiddenItemIds())
                .toString(),
            Instant.now(),
            auditorProvider.getCurrentAuditor().orElse(null));
    if (inserted.isPresent()) {
      return new NavPreferencesImportResponse(
          true, navPreferencesMapper.toResponse(inserted.get()));
    }
    UserNavPreferences existing =
        preferencesRepository
            .findByTenantIdAndUser_Id(tenantId, userId)
            .orElseThrow(() -> new NotFoundException("Nav preferences not found: " + userId));
    return new NavPreferencesImportResponse(false, navPreferencesMapper.toResponse(existing));
  }

  /**
   * Get preferences for a user. If none exist, returns default (empty sortOrder + empty
   * hiddenItemIds). Never throws 404.
   *
   * @param tenantId must be TenantContext.requireTenantId()
   * @param userId the user id
   * @return response with stored or default preferences
   */
  @Transactional(readOnly = true)
  public NavPreferencesResponse getPreferences(UUID tenantId, UUID userId) {
    if (tenantId == null || userId == null) {
      throw new PlatformDomainException(
          "tenantId and userId must not be null", "USER_PREF_INVALID_ARGS", 400);
    }
    return preferencesRepository
        .findByTenantIdAndUser_Id(tenantId, userId)
        .map(navPreferencesMapper::toResponse)
        .orElseGet(this::defaultResponse);
  }

  /**
   * Upsert preferences: update existing row (only changed fields) or create new. Null field in
   * request means "don't touch that field".
   *
   * @param tenantId must be TenantContext.requireTenantId()
   * @param userId the user id
   * @param request partial update; null sortOrder/hiddenItemIds = leave existing or use default
   * @return response with saved preferences
   */
  @Transactional
  public NavPreferencesResponse upsertPreferences(
      UUID tenantId, UUID userId, NavPreferencesRequest request) {
    if (tenantId == null || userId == null) {
      throw new PlatformDomainException(
          "tenantId and userId must not be null", "USER_PREF_INVALID_ARGS", 400);
    }
    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));

    return preferencesRepository
        .findByTenantIdAndUser_Id(tenantId, userId)
        .map(existing -> updateExistingAndReturnResponse(existing, request))
        .orElseGet(() -> createAndSaveNewResponse(user, request));
  }

  private NavPreferencesResponse updateExistingAndReturnResponse(
      UserNavPreferences existing, NavPreferencesRequest request) {
    navPreferencesMapper.updateEntityFromRequest(existing, request);
    UserNavPreferences saved = preferencesRepository.save(existing);
    return navPreferencesMapper.toResponse(saved);
  }

  private NavPreferencesResponse createAndSaveNewResponse(
      User user, NavPreferencesRequest request) {
    UserNavPreferences created = createNew(user, request);
    UserNavPreferences saved = preferencesRepository.save(created);
    return navPreferencesMapper.toResponse(saved);
  }

  private NavPreferencesResponse defaultResponse() {
    return NavPreferencesResponse.builder()
        .sortOrder(NavPreferencesConstants.DEFAULT_SORT_ORDER)
        .hiddenItemIds(NavPreferencesConstants.DEFAULT_HIDDEN_ITEM_IDS)
        .build();
  }

  private UserNavPreferences createNew(User user, NavPreferencesRequest request) {
    return UserNavPreferences.builder()
        .user(user)
        .sortOrder(
            request.getSortOrder() != null
                ? request.getSortOrder()
                : NavPreferencesConstants.DEFAULT_SORT_ORDER)
        .hiddenItemIds(
            request.getHiddenItemIds() != null
                ? request.getHiddenItemIds()
                : NavPreferencesConstants.DEFAULT_HIDDEN_ITEM_IDS)
        .build();
  }
}
