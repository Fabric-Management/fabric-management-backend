package com.fabricmanagement.common.infrastructure.tenant;

import java.util.UUID;

/** Cross-module port for tenant trial lifecycle side effects. */
public interface TrialLifecyclePort {

  int startSelfServiceTrialIfNeeded(UUID tenantId);

  void touchTenantActivity(UUID tenantId);
}
