package com.fabricmanagement.product.core.app.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import com.fabricmanagement.product.core.app.PropertyRegistryService;
import com.fabricmanagement.product.core.domain.registry.PropertyRegistryException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PropertyDefinitionBackfillRunner {

  private final PropertyDefinitionSeeder seeder;
  private final PropertyRegistryService registryService;
  private final TenantClonerService tenantClonerService;
  private final SystemTransactionExecutor systemTransactionExecutor;

  @EventListener(ApplicationReadyEvent.class)
  @Order(210) // after tenant creation and permission-template completion
  public void run() {
    try {
      seeder.seed(TenantContext.TEMPLATE_TENANT_ID);
      seeder.validateSystemRows(TenantContext.TEMPLATE_TENANT_ID);
    } catch (Exception exception) {
      log.error(
          "CRITICAL: property definition catalogue or template validation failed.", exception);
      throw new PropertyRegistryException("Property Registry startup validation failed", exception);
    }

    List<UUID> tenants =
        systemTransactionExecutor.executeInTransaction(
            jdbc ->
                jdbc.queryForList(
                    "SELECT id FROM common_tenant.common_tenant WHERE deleted_at IS NULL",
                    UUID.class));
    for (UUID tenantId : tenants) {
      if (TenantContext.TEMPLATE_TENANT_ID.equals(tenantId)) {
        continue;
      }
      logDriftedSystemRows(tenantId);
      int inserted =
          tenantClonerService.copyMissingSystemPropertyDefinitions(
              TenantContext.TEMPLATE_TENANT_ID, tenantId);
      log.info(
          "Property definition backfill: tenant={}, insertedMissingSystemKeys={}",
          tenantId,
          inserted);
    }
  }

  private void logDriftedSystemRows(UUID tenantId) {
    List<String> keys =
        systemTransactionExecutor.executeInTransaction(
            jdbc ->
                jdbc.queryForList(
                    "SELECT property_key FROM production.prod_property_definition "
                        + "WHERE tenant_id = ? AND system_defined = TRUE AND deleted_at IS NULL",
                    String.class,
                    tenantId));
    for (String key : keys) {
      try {
        TenantContext.executeInTenantContext(
            tenantId,
            () -> {
              registryService.resolve(tenantId, key);
              return null;
            });
      } catch (PropertyRegistryException drift) {
        log.error(
            "Property definition drift retained for explicit repair: tenant={}, key={}, error={}",
            tenantId,
            key,
            drift.getMessage());
      }
    }
  }
}
