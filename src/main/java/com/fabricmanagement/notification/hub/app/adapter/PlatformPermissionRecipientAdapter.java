package com.fabricmanagement.notification.hub.app.adapter;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.notification.hub.domain.port.PermissionRecipientPort;
import com.fabricmanagement.platform.user.app.UserQueryService;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves permission holders through the platform's fresh effective-permission evaluation. */
@Component
@RequiredArgsConstructor
public class PlatformPermissionRecipientAdapter implements PermissionRecipientPort {

  private final UserQueryService userQueryService;

  @Override
  public Set<UUID> findUsersWithPermission(UUID tenantId, PermissionKey permissionKey) {
    return userQueryService.findUsersWithAction(tenantId, permissionKey);
  }
}
