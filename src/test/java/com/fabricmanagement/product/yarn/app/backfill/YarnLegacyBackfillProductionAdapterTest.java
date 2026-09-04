package com.fabricmanagement.product.yarn.app.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.yarn.app.port.LegacyDesignationDiscovery;
import com.fabricmanagement.product.yarn.app.port.LegacyDesignationSourceKind;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.core.workorder.domain.WorkOrder;
import com.fabricmanagement.production.core.workorder.infra.repository.WorkOrderRepository;
import com.fabricmanagement.production.spinning.app.adapter.ProductionLegacyYarnDesignationAdapter;
import com.fabricmanagement.production.spinning.domain.specs.SpinningProductionSpecs;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class YarnLegacyBackfillProductionAdapterTest {

  @Test
  void discoversActiveSourceRowsWithoutBusinessStatusFiltering() {
    UUID tenantId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    Instant recordedAt = Instant.parse("2026-08-31T12:00:00Z");
    Batch batch = mock(Batch.class);
    when(batch.getId()).thenReturn(UUID.randomUUID());
    when(batch.getProductId()).thenReturn(productId);
    when(batch.getAttributes()).thenReturn(Map.of("yarn_count", "Batch Ne 30/2"));
    when(batch.getCreatedAt()).thenReturn(recordedAt);
    WorkOrder workOrder = mock(WorkOrder.class);
    when(workOrder.getId()).thenReturn(UUID.randomUUID());
    when(workOrder.getOutputProductId()).thenReturn(productId);
    when(workOrder.getCreatedAt()).thenReturn(recordedAt.minusSeconds(1));
    when(workOrder.getProductionSpecs())
        .thenReturn(
            new SpinningProductionSpecs(
                "Target Ne 30/2", null, null, null, null, null, null, null, null, null));
    BatchRepository batches = mock(BatchRepository.class);
    WorkOrderRepository workOrders = mock(WorkOrderRepository.class);
    when(batches.findByTenantIdAndProductTypeAndIsActiveTrue(tenantId, ProductType.YARN))
        .thenReturn(List.of(batch));
    when(workOrders.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(workOrder));

    LegacyDesignationDiscovery discovery =
        new ProductionLegacyYarnDesignationAdapter(batches, workOrders).discover(tenantId);

    assertThat(discovery.records())
        .extracting(record -> record.sourceKind())
        .containsExactly(
            LegacyDesignationSourceKind.BATCH_ACTUAL,
            LegacyDesignationSourceKind.WORK_ORDER_TARGET);
    assertThat(discovery.records())
        .extracting(record -> record.rawValue())
        .containsExactly("Batch Ne 30/2", "Target Ne 30/2");
    assertThat(discovery.unlinkedCounts()).isEmpty();
  }
}
