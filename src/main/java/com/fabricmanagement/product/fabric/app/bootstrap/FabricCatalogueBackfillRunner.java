package com.fabricmanagement.product.fabric.app.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Seeds the template's fabric constructions at startup and copies missing ones to all tenants. */
@Component
@RequiredArgsConstructor
@Slf4j
public class FabricCatalogueBackfillRunner {

  private final FabricCatalogueSeeder seeder;
  private final TenantClonerService tenantClonerService;

  @EventListener(ApplicationReadyEvent.class)
  @Order(225) // After the yarn catalogue runner (220); independent of it.
  public void run() {
    try {
      int templateInserted = seeder.seed(TenantContext.TEMPLATE_TENANT_ID);
      int tenantInserted =
          tenantClonerService.copyMissingSystemFabricCataloguesToAllTenants(
              TenantContext.TEMPLATE_TENANT_ID);
      log.info(
          "Fabric catalogue backfill completed: templateInserted={}, tenantInserted={}",
          templateInserted,
          tenantInserted);
    } catch (Exception exception) {
      log.error("CRITICAL: fabric catalogue seed or tenant propagation failed.", exception);
      throw new FabricDomainException("Fabric catalogue startup backfill failed: " + exception);
    }
  }
}
