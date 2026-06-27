package com.fabricmanagement.platform.tenant.app.adapter;

import com.fabricmanagement.common.infrastructure.tenant.TenantAccessPort;
import com.fabricmanagement.platform.tenant.app.TenantSystemService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/** Tenant lifecycle access decisions for common infrastructure guards. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantAccessAdapter implements TenantAccessPort {

  private final TenantSystemService tenantSystemService;

  @Override
  @Cacheable(value = "tenant-writable", key = "#tenantId.toString()")
  public boolean isWritable(UUID tenantId) {
    if (tenantId == null) {
      log.warn("Tenant write decision requested with null tenantId; failing open");
      return true;
    }
    return tenantSystemService
        .findById(tenantId)
        .map(tenant -> tenant.getStatus() == null || tenant.getStatus().canWrite())
        .orElseGet(
            () -> {
              log.warn(
                  "Tenant {} could not be resolved for write decision; failing open", tenantId);
              return true;
            });
  }
}
