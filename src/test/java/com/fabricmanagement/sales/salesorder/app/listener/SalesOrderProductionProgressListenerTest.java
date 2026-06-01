package com.fabricmanagement.sales.salesorder.app.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.domain.event.production.SalesOrderLineProductionCompletedEvent;
import com.fabricmanagement.common.domain.event.production.WorkOrderStartedEvent;
import com.fabricmanagement.sales.salesorder.app.ProductionProgressService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalesOrderProductionProgressListenerTest {

  @Mock private ProductionProgressService productionProgressService;

  @InjectMocks private SalesOrderProductionProgressListener listener;

  private UUID tenantId;
  private UUID workOrderId;
  private UUID salesOrderLineId;
  private UUID salesOrderId;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    workOrderId = UUID.randomUUID();
    salesOrderLineId = UUID.randomUUID();
    salesOrderId = UUID.randomUUID();
  }

  @Test
  void onWorkOrderStarted_whenLineExists_delegatesToLineAndHeaderPhases() {
    WorkOrderStartedEvent event =
        new WorkOrderStartedEvent(tenantId, workOrderId, salesOrderLineId);
    when(productionProgressService.markLineInProduction(salesOrderLineId)).thenReturn(salesOrderId);

    listener.onWorkOrderStarted(event);

    verify(productionProgressService).markLineInProduction(salesOrderLineId);
    verify(productionProgressService).markOrderInProgressIfConfirmed(salesOrderId);
  }

  @Test
  void onWorkOrderStarted_whenLineMissing_skipsHeaderPhase() {
    WorkOrderStartedEvent event =
        new WorkOrderStartedEvent(tenantId, workOrderId, salesOrderLineId);
    when(productionProgressService.markLineInProduction(salesOrderLineId)).thenReturn(null);

    listener.onWorkOrderStarted(event);

    verify(productionProgressService).markLineInProduction(salesOrderLineId);
    verify(productionProgressService, never()).markOrderInProgressIfConfirmed(any(UUID.class));
  }

  @Test
  void onSalesOrderLineProductionCompleted_delegatesToCompletionPhase() {
    SalesOrderLineProductionCompletedEvent event =
        new SalesOrderLineProductionCompletedEvent(tenantId, salesOrderLineId, workOrderId);

    listener.onSalesOrderLineProductionCompleted(event);

    verify(productionProgressService).markLineProductionCompleted(salesOrderLineId);
  }
}
