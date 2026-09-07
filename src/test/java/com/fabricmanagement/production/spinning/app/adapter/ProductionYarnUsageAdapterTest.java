package com.fabricmanagement.production.spinning.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.product.yarn.app.port.YarnUsageSignal;
import com.fabricmanagement.production.core.inventory.infra.repository.InventoryTransactionRepository;
import com.fabricmanagement.production.core.workorder.infra.repository.WorkOrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductionYarnUsageAdapterTest {

  @Mock private WorkOrderRepository workOrderRepository;
  @Mock private InventoryTransactionRepository transactionRepository;

  @Test
  void discoversEachLegWithTheFixedNinetyDayLedgerCutoff() {
    UUID tenantId = UUID.randomUUID();
    UUID workOrderProduct = UUID.randomUUID();
    UUID movementProduct = UUID.randomUUID();
    Instant now = Instant.parse("2026-09-01T12:00:00Z");
    when(workOrderRepository.findOpenReferencedProductIds(tenantId))
        .thenReturn(List.of(workOrderProduct));
    when(workOrderRepository.countOpenUnlinkedSpinningWorkOrders(tenantId)).thenReturn(2L);
    when(transactionRepository.findRecentlyMovedProductIds(eq(tenantId), any(), any()))
        .thenReturn(List.of(movementProduct));

    var discovery =
        new ProductionYarnUsageAdapter(
                workOrderRepository, transactionRepository, Clock.fixed(now, ZoneOffset.UTC))
            .discover(tenantId);

    assertThat(discovery.referencedProductIds().get(YarnUsageSignal.OPEN_WORK_ORDER))
        .containsExactly(workOrderProduct);
    assertThat(discovery.referencedProductIds().get(YarnUsageSignal.RECENT_BATCH_MOVEMENT))
        .containsExactly(movementProduct);
    assertThat(discovery.unlinkedYarnDocumentCounts())
        .containsEntry(YarnUsageSignal.OPEN_WORK_ORDER, 2L);
    verify(transactionRepository)
        .findRecentlyMovedProductIds(
            eq(tenantId),
            eq(Instant.parse("2026-06-03T12:00:00Z")),
            eq(
                ProductionYarnUsageAdapter.PHYSICAL_MOVEMENT_TYPES.stream()
                    .map(Enum::name)
                    .collect(Collectors.toSet())));
  }
}
