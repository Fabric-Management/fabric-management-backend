package com.fabricmanagement.common.platform.user.app;

import com.fabricmanagement.common.platform.user.domain.value.ProfileCategory;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Field-level access control for user profile updates.
 *
 * <p>Delegates to {@link TeamAccessService} for department-based permission model:
 *
 * <ul>
 *   <li>FULL_ACCESS (Admin role / Admin Office / HR dept): Full access to all profiles
 *   <li>Self-update: Always denied (must go through update request flow)
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfilePermissionService {

  private final TeamAccessService teamAccessService;

  @Transactional(readOnly = true)
  public boolean canUpdateWorkProfile(UUID requesterId, UUID targetUserId) {
    if (requesterId.equals(targetUserId)) {
      return false;
    }
    return teamAccessService.canManageMembers(requesterId);
  }

  @Transactional(readOnly = true)
  public boolean canUpdatePersonalProfile(UUID requesterId, UUID targetUserId) {
    if (requesterId.equals(targetUserId)) {
      return false;
    }
    return teamAccessService.canManageMembers(requesterId);
  }

  @Transactional(readOnly = true)
  public boolean canViewProfile(UUID requesterId, UUID targetUserId, ProfileCategory category) {
    if (requesterId.equals(targetUserId)) {
      return true;
    }
    return teamAccessService.canViewAllMembers(requesterId);
  }
}
