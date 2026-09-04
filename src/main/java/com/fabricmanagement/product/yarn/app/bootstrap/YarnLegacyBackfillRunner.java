package com.fabricmanagement.product.yarn.app.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.yarn.app.backfill.YarnLegacyBackfillReport;
import com.fabricmanagement.product.yarn.app.backfill.YarnLegacyBackfillService;
import java.util.List;
import java.util.Map;
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
public class YarnLegacyBackfillRunner {

  private final YarnLegacyBackfillService backfillService;
  private final SystemTransactionExecutor systemTransactionExecutor;

  @EventListener(ApplicationReadyEvent.class)
  @Order(230) // YarnCatalogueBackfillRunner is @Order(220); catalogue references land first.
  public void run() {
    List<Map.Entry<UUID, String>> tenants =
        systemTransactionExecutor.executeInTransaction(
            jdbc ->
                jdbc.query(
                    "SELECT id, uid FROM common_tenant.common_tenant WHERE deleted_at IS NULL "
                        + "ORDER BY id",
                    (resultSet, rowNumber) ->
                        Map.entry(
                            resultSet.getObject("id", UUID.class), resultSet.getString("uid"))));
    for (Map.Entry<UUID, String> tenant : tenants) {
      UUID tenantId = tenant.getKey();
      YarnLegacyBackfillReport report =
          TenantContext.executeInTenantContext(
              tenantId,
              () -> {
                TenantContext.setCurrentTenantUid(tenant.getValue());
                return backfillService.backfillTenant(tenantId);
              });
      log.info("Yarn legacy backfill report: {}", report);
    }
  }
}
