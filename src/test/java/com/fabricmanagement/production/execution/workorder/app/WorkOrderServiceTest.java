package com.fabricmanagement.production.execution.workorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.domain.event.production.WorkOrderStartedEvent;
import com.fabricmanagement.common.infrastructure.approval.ApprovalPort;
import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.DocumentNumberGenerator;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.api.facade.TenantFacade;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.workorder.app.adapter.TradingPartnerAdapter;
import com.fabricmanagement.production.execution.workorder.domain.FulfillmentType;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderCompletedEvent;
import com.fabricmanagement.production.execution.workorder.dto.StartProductionRequest;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderResponse;
import com.fabricmanagement.production.execution.workorder.infra.repository.ProductionRecordRepository;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderConsumptionRepository;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

  @Mock private WorkOrderRepository workOrderRepository;
  @Mock private WorkOrderConsumptionRepository workOrderConsumptionRepository;
  @Mock private ProductionRecordRepository productionRecordRepository;
  @Mock private BatchRepository batchRepository;
  @Mock private TradingPartnerAdapter tradingPartnerAdapter;
  @Mock private DomainEventPublisher domainEventPublisher;
  @Mock private ApprovalPort approvalPort;
  @Mock private TenantFacade tenantFacade;
  @Mock private DocumentNumberGenerator documentNumberGenerator;
  @Mock private WorkOrderProductionCompletionService workOrderProductionCompletionService;

  @InjectMocks private WorkOrderService workOrderService;

  private UUID tenantId;
  private UUID workOrderId;
  private UUID salesOrderLineId;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    workOrderId = UUID.randomUUID();
    salesOrderLineId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void startProduction_whenLinkedToSalesOrderLine_publishesWorkOrderStartedEvent() {
    WorkOrder workOrder =
        WorkOrder.builder()
            .workOrderNumber("WO-001")
            .recipeId(UUID.randomUUID())
            .salesOrderLineId(salesOrderLineId)
            .status(WorkOrderStatus.SENT)
            .moduleType(WorkOrderModuleType.GENERIC)
            .fulfillmentType(FulfillmentType.INTERNAL)
            .plannedQty(new BigDecimal("100"))
            .unit("KG")
            .build();
    workOrder.setId(workOrderId);
    workOrder.setTenantId(tenantId);
    workOrder.setIsActive(true);

    UUID outputProductId = UUID.randomUUID();
    StartProductionRequest request =
        StartProductionRequest.builder().outputProductId(outputProductId).build();

    when(workOrderRepository.findByIdAndTenantIdAndIsActiveTrue(workOrderId, tenantId))
        .thenReturn(Optional.of(workOrder));
    when(workOrderRepository.save(any(WorkOrder.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    WorkOrderResponse response = workOrderService.startProduction(workOrderId, request);

    assertThat(response.status()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
    assertThat(response.outputProductId()).isEqualTo(outputProductId);

    ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(domainEventPublisher).publish(eventCaptor.capture());

    assertThat(eventCaptor.getValue()).isInstanceOf(WorkOrderStartedEvent.class);
    WorkOrderStartedEvent event = (WorkOrderStartedEvent) eventCaptor.getValue();
    assertThat(event.getTenantId()).isEqualTo(tenantId);
    assertThat(event.getWorkOrderId()).isEqualTo(workOrderId);
    assertThat(event.getSalesOrderLineId()).isEqualTo(salesOrderLineId);
  }

  @Test
  void completeWorkOrder_whenLinkedToSalesOrderLine_publishesCompletionPayloadAndChecksLine() {
    WorkOrder workOrder =
        WorkOrder.builder()
            .workOrderNumber("WO-001")
            .salesOrderLineId(salesOrderLineId)
            .status(WorkOrderStatus.IN_PROGRESS)
            .moduleType(WorkOrderModuleType.GENERIC)
            .fulfillmentType(FulfillmentType.INTERNAL)
            .plannedQty(new BigDecimal("100"))
            .unit("KG")
            .build();
    workOrder.setId(workOrderId);
    workOrder.setTenantId(tenantId);
    workOrder.setIsActive(true);

    when(workOrderRepository.findByIdAndTenantIdAndIsActiveTrue(workOrderId, tenantId))
        .thenReturn(Optional.of(workOrder));
    when(workOrderConsumptionRepository.sumConsumedWeightByWorkOrderId(tenantId, workOrderId))
        .thenReturn(new BigDecimal("100"));
    when(productionRecordRepository.sumOutputWeightByWorkOrderId(tenantId, workOrderId))
        .thenReturn(new BigDecimal("90"));
    when(workOrderRepository.save(any(WorkOrder.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(batchRepository.findByTenantIdAndSourceIdAndSourceTypeAndStatusAndIsActiveTrue(
            any(), any(), any(), any()))
        .thenReturn(List.of());

    WorkOrderResponse response = workOrderService.completeWorkOrder(workOrderId);

    assertThat(response.status()).isEqualTo(WorkOrderStatus.COMPLETED);

    ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(domainEventPublisher).publish(eventCaptor.capture());

    assertThat(eventCaptor.getValue()).isInstanceOf(WorkOrderCompletedEvent.class);
    WorkOrderCompletedEvent event = (WorkOrderCompletedEvent) eventCaptor.getValue();
    assertThat(event.getTenantId()).isEqualTo(tenantId);
    assertThat(event.getWorkOrderId()).isEqualTo(workOrderId);
    assertThat(event.getSalesOrderLineId()).isEqualTo(salesOrderLineId);

    verify(workOrderProductionCompletionService)
        .publishLineCompletedIfAllWorkOrdersCompleted(tenantId, salesOrderLineId, workOrderId);
  }
}
