package com.fabricmanagement.production.execution.workorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.workorder.app.port.ComputedCostSnapshot;
import com.fabricmanagement.production.execution.workorder.app.port.WorkOrderCostEnginePort;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.domain.exception.WorkOrderDomainException;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderResponse;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkOrderPlannedCostTriggerService")
class WorkOrderPlannedCostTriggerServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID WORK_ORDER_ID = UUID.randomUUID();
  private static final UUID PRODUCT_ID = UUID.randomUUID();
  private static final UUID TRADING_PARTNER_ID = UUID.randomUUID();
  private static final WorkOrderModuleType MODULE_TYPE = WorkOrderModuleType.SPINNING;
  private static final BigDecimal PLANNED_QTY = new BigDecimal("1000.000");

  @Mock private WorkOrderCostEnginePort costEnginePort;
  @Mock private WorkOrderRepository workOrderRepository;

  @InjectMocks private WorkOrderPlannedCostTriggerService triggerService;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private WorkOrder buildWorkOrder(
      WorkOrderStatus status, UUID outputProductId, WorkOrderModuleType moduleType) {
    WorkOrder wo =
        WorkOrder.builder()
            .workOrderNumber("WO-TEST-001")
            .status(status)
            .outputProductId(outputProductId)
            .moduleType(moduleType)
            .plannedQty(PLANNED_QTY)
            .unit("KG")
            .tradingPartnerId(TRADING_PARTNER_ID)
            .build();
    wo.setId(WORK_ORDER_ID);
    wo.setTenantId(TENANT_ID);
    wo.setIsActive(true);
    return wo;
  }

  @Nested
  @DisplayName("triggerPlannedCost() — guard validations")
  class GuardValidationTests {

    @Test
    @DisplayName("throws when WorkOrder not found")
    void throwsWhenWorkOrderNotFound() {
      when(workOrderRepository.findById(WORK_ORDER_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> triggerService.triggerPlannedCost(WORK_ORDER_ID))
          .isInstanceOf(WorkOrderDomainException.class)
          .hasMessageContaining("WorkOrder not found");

      verify(costEnginePort, never()).computePlannedCost(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("throws when WorkOrder belongs to different tenant")
    void throwsWhenWrongTenant() {
      WorkOrder wo = buildWorkOrder(WorkOrderStatus.APPROVED, PRODUCT_ID, MODULE_TYPE);
      wo.setTenantId(UUID.randomUUID()); // different tenant

      when(workOrderRepository.findById(WORK_ORDER_ID)).thenReturn(Optional.of(wo));

      assertThatThrownBy(() -> triggerService.triggerPlannedCost(WORK_ORDER_ID))
          .isInstanceOf(WorkOrderDomainException.class)
          .hasMessageContaining("WorkOrder not found");

      verify(costEnginePort, never()).computePlannedCost(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("throws when WorkOrder is soft-deleted")
    void throwsWhenSoftDeleted() {
      WorkOrder wo = buildWorkOrder(WorkOrderStatus.APPROVED, PRODUCT_ID, MODULE_TYPE);
      wo.setIsActive(false);

      when(workOrderRepository.findById(WORK_ORDER_ID)).thenReturn(Optional.of(wo));

      assertThatThrownBy(() -> triggerService.triggerPlannedCost(WORK_ORDER_ID))
          .isInstanceOf(WorkOrderDomainException.class)
          .hasMessageContaining("WorkOrder not found");

      verify(costEnginePort, never()).computePlannedCost(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("throws when WorkOrder is in DRAFT status")
    void throwsWhenDraftStatus() {
      WorkOrder wo = buildWorkOrder(WorkOrderStatus.DRAFT, PRODUCT_ID, MODULE_TYPE);
      when(workOrderRepository.findById(WORK_ORDER_ID)).thenReturn(Optional.of(wo));

      assertThatThrownBy(() -> triggerService.triggerPlannedCost(WORK_ORDER_ID))
          .isInstanceOf(WorkOrderDomainException.class)
          .hasMessageContaining("Planned cost requires at least APPROVED status")
          .hasMessageContaining("DRAFT");

      verify(costEnginePort, never()).computePlannedCost(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("throws when WorkOrder is in PENDING_APPROVAL status")
    void throwsWhenPendingApprovalStatus() {
      WorkOrder wo = buildWorkOrder(WorkOrderStatus.PENDING_APPROVAL, PRODUCT_ID, MODULE_TYPE);
      when(workOrderRepository.findById(WORK_ORDER_ID)).thenReturn(Optional.of(wo));

      assertThatThrownBy(() -> triggerService.triggerPlannedCost(WORK_ORDER_ID))
          .isInstanceOf(WorkOrderDomainException.class)
          .hasMessageContaining("Planned cost requires at least APPROVED status")
          .hasMessageContaining("PENDING_APPROVAL");

      verify(costEnginePort, never()).computePlannedCost(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("throws when outputProductId is null (pre-Sprint 12 WO)")
    void throwsWhenOutputProductIdMissing() {
      WorkOrder wo = buildWorkOrder(WorkOrderStatus.APPROVED, null, MODULE_TYPE);
      when(workOrderRepository.findById(WORK_ORDER_ID)).thenReturn(Optional.of(wo));

      assertThatThrownBy(() -> triggerService.triggerPlannedCost(WORK_ORDER_ID))
          .isInstanceOf(WorkOrderDomainException.class)
          .hasMessageContaining("missing outputProductId");

      verify(costEnginePort, never()).computePlannedCost(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("throws when moduleType is null")
    void throwsWhenModuleTypeMissing() {
      WorkOrder wo = buildWorkOrder(WorkOrderStatus.APPROVED, PRODUCT_ID, null);
      when(workOrderRepository.findById(WORK_ORDER_ID)).thenReturn(Optional.of(wo));

      assertThatThrownBy(() -> triggerService.triggerPlannedCost(WORK_ORDER_ID))
          .isInstanceOf(WorkOrderDomainException.class)
          .hasMessageContaining("missing moduleType");

      verify(costEnginePort, never()).computePlannedCost(any(), any(), any(), any(), any(), any());
    }
  }

  @Nested
  @DisplayName("triggerPlannedCost() — happy path")
  class HappyPathTests {

    @Test
    @DisplayName("computes planned cost for APPROVED WorkOrder and returns updated response")
    void computesPlannedCostForApprovedWorkOrder() {
      WorkOrder wo = buildWorkOrder(WorkOrderStatus.APPROVED, PRODUCT_ID, MODULE_TYPE);
      BigDecimal computedCost = new BigDecimal("5250.500");

      when(workOrderRepository.findById(WORK_ORDER_ID)).thenReturn(Optional.of(wo));
      when(costEnginePort.computePlannedCost(
              TENANT_ID, WORK_ORDER_ID, MODULE_TYPE, PRODUCT_ID, PLANNED_QTY, TRADING_PARTNER_ID))
          .thenReturn(new ComputedCostSnapshot(WORK_ORDER_ID, computedCost, "TRY"));

      // After write-back reload
      WorkOrder updatedWo = buildWorkOrder(WorkOrderStatus.APPROVED, PRODUCT_ID, MODULE_TYPE);
      updatedWo.setPlannedCost(computedCost);
      updatedWo.setPlannedCostCurrency("TRY");
      when(workOrderRepository.findById(WORK_ORDER_ID))
          .thenReturn(Optional.of(wo))
          .thenReturn(Optional.of(updatedWo));

      WorkOrderResponse response = triggerService.triggerPlannedCost(WORK_ORDER_ID);

      verify(costEnginePort)
          .computePlannedCost(
              eq(TENANT_ID),
              eq(WORK_ORDER_ID),
              eq(MODULE_TYPE),
              eq(PRODUCT_ID),
              eq(PLANNED_QTY),
              eq(TRADING_PARTNER_ID));

      assertThat(response).isNotNull();
      assertThat(response.plannedCost()).isEqualTo(computedCost);
      assertThat(response.plannedCostCurrency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("works for COMPLETED WorkOrders (idempotent recalculation)")
    void worksForCompletedWorkOrders() {
      WorkOrder wo = buildWorkOrder(WorkOrderStatus.COMPLETED, PRODUCT_ID, MODULE_TYPE);
      BigDecimal computedCost = new BigDecimal("4800.000");

      when(workOrderRepository.findById(WORK_ORDER_ID)).thenReturn(Optional.of(wo));
      when(costEnginePort.computePlannedCost(any(), any(), any(), any(), any(), any()))
          .thenReturn(new ComputedCostSnapshot(WORK_ORDER_ID, computedCost, "TRY"));

      WorkOrderResponse response = triggerService.triggerPlannedCost(WORK_ORDER_ID);

      assertThat(response).isNotNull();
      verify(costEnginePort).computePlannedCost(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("works for IN_PROGRESS WorkOrders")
    void worksForInProgressWorkOrders() {
      WorkOrder wo = buildWorkOrder(WorkOrderStatus.IN_PROGRESS, PRODUCT_ID, MODULE_TYPE);

      when(workOrderRepository.findById(WORK_ORDER_ID)).thenReturn(Optional.of(wo));
      when(costEnginePort.computePlannedCost(any(), any(), any(), any(), any(), any()))
          .thenReturn(new ComputedCostSnapshot(WORK_ORDER_ID, BigDecimal.TEN, "TRY"));

      WorkOrderResponse response = triggerService.triggerPlannedCost(WORK_ORDER_ID);

      assertThat(response).isNotNull();
    }
  }
}
