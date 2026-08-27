package com.fabricmanagement.product.yarn.app.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(220) // PropertyDefinitionBackfillRunner is @Order(210); registry must exist first.
public class YarnCatalogueBackfillRunner {

  private final YarnCatalogueSeeder seeder;
  private final TenantClonerService tenantClonerService;

  @EventListener(ApplicationReadyEvent.class)
  public void run() {
    try {
      int templateInserted = seeder.seed(TenantContext.TEMPLATE_TENANT_ID);
      int tenantInserted =
          tenantClonerService.copyMissingSystemYarnCataloguesToAllTenants(
              TenantContext.TEMPLATE_TENANT_ID);
      log.info(
          "Yarn catalogue backfill completed: templateInserted={}, tenantInserted={}",
          templateInserted,
          tenantInserted);
    } catch (Exception exception) {
      log.error("CRITICAL: yarn catalogue seed or tenant propagation failed.", exception);
      throw new YarnDomainException("Yarn catalogue startup backfill failed", exception);
    }
  }
}
