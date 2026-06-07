package com.fabricmanagement.production.execution.workorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.domain.event.production.SalesOrderLineProductionCompletedEvent;
import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkOrderProductionCompletionServiceTest {

  @Mock private WorkOrderRepository workOrderRepository;
  @Mock private DomainEventPublisher domainEventPublisher;

  private WorkOrderProductionCompletionService service;
  private UUID tenantId;
  private UUID salesOrderLineId;
  private UUID completedByWorkOrderId;

  @BeforeEach
  void setUp() {
    service = new WorkOrderProductionCompletionService(workOrderRepository, domainEventPublisher);
    tenantId = UUID.randomUUID();
    salesOrderLineId = UUID.randomUUID();
    completedByWorkOrderId = UUID.randomUUID();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void publishLineCompletedIfAllWorkOrdersCompleted_whenOpenWorkOrderExists_skipsEvent() {
    when(workOrderRepository.existsByTenantIdAndSalesOrderLineIdAndIsActiveTrueAndStatusNotIn(
            tenantId,
            salesOrderLineId,
            List.of(WorkOrderStatus.COMPLETED, WorkOrderStatus.CANCELLED)))
        .thenReturn(true);

    service.publishLineCompletedIfAllWorkOrdersCompleted(
        tenantId, salesOrderLineId, completedByWorkOrderId);

    verify(domainEventPublisher, never()).publish(any(DomainEvent.class));
  }

  @Test
  void publishLineCompletedIfAllWorkOrdersCompleted_whenAllClosed_publishesLineEvent() {
    when(workOrderRepository.existsByTenantIdAndSalesOrderLineIdAndIsActiveTrueAndStatusNotIn(
            tenantId,
            salesOrderLineId,
            List.of(WorkOrderStatus.COMPLETED, WorkOrderStatus.CANCELLED)))
        .thenReturn(false);

    service.publishLineCompletedIfAllWorkOrdersCompleted(
        tenantId, salesOrderLineId, completedByWorkOrderId);

    ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(domainEventPublisher).publish(eventCaptor.capture());

    assertThat(eventCaptor.getValue()).isInstanceOf(SalesOrderLineProductionCompletedEvent.class);
    SalesOrderLineProductionCompletedEvent event =
        (SalesOrderLineProductionCompletedEvent) eventCaptor.getValue();
    assertThat(event.getTenantId()).isEqualTo(tenantId);
    assertThat(event.getSalesOrderLineId()).isEqualTo(salesOrderLineId);
    assertThat(event.getCompletedByWorkOrderId()).isEqualTo(completedByWorkOrderId);
  }
}
