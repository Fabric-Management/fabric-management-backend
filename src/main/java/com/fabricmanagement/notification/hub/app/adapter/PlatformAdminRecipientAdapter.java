package com.fabricmanagement.notification.hub.app.adapter;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.notification.hub.domain.port.PlatformAdminRecipientPort;
import com.fabricmanagement.platform.user.app.UserQueryService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Platform administrators are the system tenant's users with the {@code PLATFORM_ADMIN} role. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlatformAdminRecipientAdapter implements PlatformAdminRecipientPort {

  static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";

  private final UserQueryService userQueryService;

  @Override
  public List<UUID> findPlatformAdministrators() {
    List<UUID> administrators =
        userQueryService.findActiveUserIdsByRoleCodes(
            TenantContext.SYSTEM_TENANT_ID, List.of(PLATFORM_ADMIN_ROLE));
    if (administrators.isEmpty()) {
      log.warn(
          "No active {} user in the platform tenant: platform notifications reach nobody",
          PLATFORM_ADMIN_ROLE);
    }
    return administrators;
  }
}
