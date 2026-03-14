package com.fabricmanagement.common.platform.user.app;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.user.domain.UserNavPreferences;
import com.fabricmanagement.common.platform.user.dto.NavPreferencesMapper;
import com.fabricmanagement.common.platform.user.dto.NavPreferencesRequest;
import com.fabricmanagement.common.platform.user.dto.NavPreferencesResponse;
import com.fabricmanagement.common.platform.user.infra.repository.UserNavPreferencesRepository;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for user nav preferences. Stays within the user module; no facade.
 *
 * <p>All calls must be tenant-scoped: pass {@code TenantContext.getCurrentTenantId()} as {@code
 * tenantId}.
 */
@Service
@RequiredArgsConstructor
public class UserNavPreferencesService {

  private final UserNavPreferencesRepository preferencesRepository;
  private final UserRepository userRepository;
  private final NavPreferencesMapper navPreferencesMapper;

  /**
   * Get preferences for a user. If none exist, returns default (empty sortOrder + empty
   * hiddenItemIds). Never throws 404.
   *
   * @param tenantId must be TenantContext.getCurrentTenantId()
   * @param userId the user id
   * @return response with stored or default preferences
   */
  @Transactional(readOnly = true)
  public NavPreferencesResponse getPreferences(UUID tenantId, UUID userId) {
    if (tenantId == null || userId == null) {
      throw new IllegalArgumentException("tenantId and userId must not be null");
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
   * @param tenantId must be TenantContext.getCurrentTenantId()
   * @param userId the user id
   * @param request partial update; null sortOrder/hiddenItemIds = leave existing or use default
   * @return response with saved preferences
   */
  @Transactional
  public NavPreferencesResponse upsertPreferences(
      UUID tenantId, UUID userId, NavPreferencesRequest request) {
    if (tenantId == null || userId == null) {
      throw new IllegalArgumentException("tenantId and userId must not be null");
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
