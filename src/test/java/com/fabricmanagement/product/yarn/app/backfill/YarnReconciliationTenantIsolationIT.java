package com.fabricmanagement.product.yarn.app.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.yarn.app.port.LegacyDesignationSourceKind;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillQueueStatus;
import com.fabricmanagement.product.yarn.dto.YarnReconciliationChooseRequest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

class YarnReconciliationTenantIsolationIT extends YarnBackfillIntegrationSupport {

  @Autowired private YarnReconciliationService reconciliationService;
  @Autowired private YarnReadinessService readinessService;

  @Test
  void everyQueuePathAndReadinessAreTenantScoped() {
    TenantFixture tenantA = insertTenant("isolation-a", 1);
    TenantFixture tenantB = insertTenant("isolation-b", 1);
    insertBatch(tenantA, 0, "Ne 20/1", Instant.parse("2026-09-01T10:00:00Z"));
    insertBatch(tenantA, 0, "Ne 40/1", Instant.parse("2026-09-01T09:00:00Z"));
    UUID tenantBRecord = insertBatch(tenantB, 0, "Ne 30/1", Instant.parse("2026-09-01T10:00:00Z"));
    insertBatch(tenantB, 0, "Ne 50/1", Instant.parse("2026-09-01T09:00:00Z"));
    backfill(tenantA);
    backfill(tenantB);
    UUID tenantBRow = reconciliationId(tenantB);
    insertOpenWorkOrder(tenantB);

    var tenantAList =
        TenantContext.executeInTenantContext(
            tenantA.tenantId(),
            () -> reconciliationService.list(YarnBackfillQueueStatus.OPEN, PageRequest.of(0, 20)));
    assertThat(tenantAList.getContent())
        .extracting("productId")
        .containsExactly(tenantA.productId(0));
    assertThatThrownBy(
            () ->
                TenantContext.executeInTenantContext(
                    tenantA.tenantId(), () -> reconciliationService.candidates(tenantBRow, 0, 50)))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                TenantContext.executeInTenantContext(
                    tenantA.tenantId(), () -> reconciliationService.dismiss(tenantBRow)))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                TenantContext.executeInTenantContext(
                    tenantA.tenantId(),
                    () ->
                        reconciliationService.choose(
                            tenantBRow,
                            new YarnReconciliationChooseRequest(
                                LegacyDesignationSourceKind.BATCH_ACTUAL,
                                tenantBRecord.toString()))))
        .isInstanceOf(NotFoundException.class);

    var readinessA =
        TenantContext.executeInTenantContext(
            tenantA.tenantId(), () -> readinessService.readiness(50));
    var readinessB =
        TenantContext.executeInTenantContext(
            tenantB.tenantId(), () -> readinessService.readiness(50));
    assertThat(readinessA.activelyUsedCount()).isZero();
    assertThat(readinessA.ready()).isTrue();
    assertThat(readinessB.activelyUsedCount()).isEqualTo(1);
    assertThat(readinessB.blockers()).extracting("productId").containsExactly(tenantB.productId(0));
  }

  private UUID reconciliationId(TenantFixture fixture) {
    return queryOne(
        "SELECT id FROM production.prod_yarn_backfill_reconciliation WHERE tenant_id=?",
        UUID.class,
        fixture.tenantId());
  }

  private void insertOpenWorkOrder(TenantFixture fixture) {
    UUID id = UUID.randomUUID();
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO production.prod_work_order "
                  + "(id, tenant_id, uid, work_order_number, output_product_id, module_type, "
                  + "production_specs, fulfillment_type, planned_qty, unit, status, is_active, "
                  + "created_at, updated_at) "
                  + "VALUES (?, ?, ?, ?, ?, 'SPINNING', "
                  + "'{\"specType\":\"SPINNING\",\"targetYarnCount\":\"Ne 30/1\"}'::jsonb, "
                  + "'INTERNAL', 1, 'KG', 'DRAFT', TRUE, NOW(), NOW())",
              id,
              fixture.tenantId(),
              "YRI-WO-" + id,
              "YRI-WO-NO-" + id,
              fixture.productId(0));
          return null;
        });
  }
}
