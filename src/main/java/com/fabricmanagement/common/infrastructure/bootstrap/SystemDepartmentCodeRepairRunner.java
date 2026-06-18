package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.platform.organization.app.SystemDepartmentCodeRepairService;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Runs the canonical system-department repair after seed data has had a chance to create tenants.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SystemDepartmentCodeRepairRunner
    implements ApplicationListener<ApplicationReadyEvent>, Ordered {

  private final TenantRepository tenantRepository;
  private final SystemDepartmentCodeRepairService repairService;

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    int repaired =
        tenantRepository.findAllActive().stream()
            .mapToInt(tenant -> repairService.repairTenant(tenant.getId()))
            .sum();
    if (repaired > 0) {
      log.info("Repaired {} system department code rows across active tenants.", repaired);
    }
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
