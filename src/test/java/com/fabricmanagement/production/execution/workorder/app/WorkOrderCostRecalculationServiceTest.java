package com.fabricmanagement.production.execution.workorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchSourceType;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.workorder.app.port.ComputedCostSnapshot;
import com.fabricmanagement.production.execution.workorder.app.port.ConsumptionCostInput;
import com.fabricmanagement.production.execution.workorder.app.port.WorkOrderCostEnginePort;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderConsumption;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.domain.exception.WorkOrderDomainException;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderResponse;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderConsumptionRepository;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import java.math.BigDecimal;
import java.util.List;
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
@DisplayName("WorkOrderCostRecalculationService")
class WorkOrderCostRecalculationServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID WORK_ORDER_ID = UUID.randomUUID();
  private static final UUID BATCH_ID = UUID.randomUUID();
  private static final UUID MATERIAL_ID = UUID.randomUUID();
  private static final UUID TRADING_PARTNER_ID = UUID.randomUUID();

  @Mock private WorkOrderCostEnginePort costEnginePort;
  @Mock private BatchRepository batchRepository;
  @Mock private WorkOrderConsumptionRepository workOrderConsumptionRepository;
  @Mock private WorkOrderRepository workOrderRepository;

  @InjectMocks private WorkOrderCostRecalculationService recalculationService;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private WorkOrder buildCompletedWorkOrder() {
    WorkOrder wo =
        WorkOrder.builder()
            .workOrderNumber("WO-TEST-001")
            .status(WorkOrderStatus.COMPLETED)
            .outputMaterialId(MATERIAL_ID)
            .moduleType("YARN")
            .plannedQty(new BigDecimal("1000.000"))
            .actualQty(new BigDecimal("950.000"))
            .unit("KG")
            .tradingPartnerId(TRADING_PARTNER_ID)
            .build();
    wo.setId(WORK_ORDER_ID);
    wo.setTenantId(TENANT_ID);
    wo.setIsActive(true);
    return wo;
  }

  private Batch buildOutputBatch() {
    Batch batch =
        Batch.builder()
            .materialId(MATERIAL_ID)
            .materialType(MaterialType.YARN)
            .sourceType(BatchSourceType.INTERNAL_PRODUCTION)
            .sourceId(WORK_ORDER_ID)
            .batchCode("BATCH-001")
            .build();
    batch.setId(BATCH_ID);
    batch.setTenantId(TENANT_ID);
    batch.setIsActive(true);
    return batch;
  }

  private WorkOrderConsumption buildConsumption(UUID materialId) {
    return WorkOrderConsumption.record(
        TENANT_ID,
        WORK_ORDER_ID,
        UUID.randomUUID(), // stockUnitId
        UUID.randomUUID(), // batchId
        "BARCODE-001",
        "BATCH-SRC-001",
        MaterialType.FIBER,
        materialId,
        new BigDecimal("500.000"),
        "KG",
        null, // qualityGradeId
        UUID.randomUUID()); // consumedBy
  }

  @Nested
  @DisplayName("recalculateActualCost() — guard validations")
  class GuardValidationTests {

    @Test
    @DisplayName("throws when WorkOrder not found")
    void throwsWhenWorkOrderNotFound() {
      when(workOrderRepository.findById(WORK_ORDER_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> recalculationService.recalculateActualCost(WORK_ORDER_ID))
          .isInstanceOf(WorkOrderDomainException.class)
          .hasMessageContaining("WorkOrder not found");

      verify(costEnginePort, never())
          .computeActualCostFromConsumptions(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("throws when WorkOrder belongs to different tenant")
    void throwsWhenWrongTenant() {
      WorkOrder wo = buildCompletedWorkOrder();
      wo.setTenantId(UUID.randomUUID());

      when(workOrderRepository.findById(WORK_ORDER_ID)).thenReturn(Optional.of(wo));

      assertThatThrownBy(() -> recalculationService.recalculateActualCost(WORK_ORDER_ID))
          .isInstanceOf(WorkOrderDomainException.class)
          .hasMessageContaining("WorkOrder not found");
    }

    @Test
    @DisplayName("throws when WorkOrder is not COMPLETED")
    void throwsWhenNotCompleted() {
      WorkOrder wo = buildCompletedWorkOrder();
      wo.setStatus(WorkOrderStatus.IN_PROGRESS);

      when(workOrderRepository.findById(WORK_ORDER_ID)).thenReturn(Optional.of(wo));

      assertThatThrownBy(() -> recalculationService.recalculateActualCost(WORK_ORDER_ID))
          .isInstanceOf(WorkOrderDomainException.class)
          .hasMessageContaining("Cost recalculation requires COMPLETED status")
          .hasMessageContaining("IN_PROGRESS");
    }

    @Test
    @DisplayName("throws when no output batch exists")
    void throwsWhenNoOutputBatch() {
      WorkOrder wo = buildCompletedWorkOrder();
      when(workOrderRepository.findById(WORK_ORDER_ID)).thenReturn(Optional.of(wo));
      when(batchRepository.findFirstByTenantIdAndSourceIdAndSourceType(
              TENANT_ID, WORK_ORDER_ID, BatchSourceType.INTERNAL_PRODUCTION))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> recalculationService.recalculateActualCost(WORK_ORDER_ID))
          .isInstanceOf(WorkOrderDomainException.class)
          .hasMessageContaining("No output batch found");
    }

    @Test
    @DisplayName("throws when no consumption records exist")
    void throwsWhenNoConsumptions() {
      WorkOrder wo = buildCompletedWorkOrder();
      Batch batch = buildOutputBatch();

      when(workOrderRepository.findById(WORK_ORDER_ID)).thenReturn(Optional.of(wo));
      when(batchRepository.findFirstByTenantIdAndSourceIdAndSourceType(
              TENANT_ID, WORK_ORDER_ID, BatchSourceType.INTERNAL_PRODUCTION))
          .thenReturn(Optional.of(batch));
      when(workOrderConsumptionRepository
              .findByTenantIdAndWorkOrderIdAndIsActiveTrueOrderByCreatedAtAsc(
                  TENANT_ID, WORK_ORDER_ID))
          .thenReturn(List.of());

      assertThatThrownBy(() -> recalculationService.recalculateActualCost(WORK_ORDER_ID))
          .isInstanceOf(WorkOrderDomainException.class)
          .hasMessageContaining("No consumption records found");
    }

    @Test
    @DisplayName("throws when consumptions exist but none have materialId (pre-Sprint 6)")
    void throwsWhenConsumptionsLackMaterialId() {
      WorkOrder wo = buildCompletedWorkOrder();
      Batch batch = buildOutputBatch();
      WorkOrderConsumption consumptionWithoutMaterial = buildConsumption(null);

      when(workOrderRepository.findById(WORK_ORDER_ID)).thenReturn(Optional.of(wo));
      when(batchRepository.findFirstByTenantIdAndSourceIdAndSourceType(
              TENANT_ID, WORK_ORDER_ID, BatchSourceType.INTERNAL_PRODUCTION))
          .thenReturn(Optional.of(batch));
      when(workOrderConsumptionRepository
              .findByTenantIdAndWorkOrderIdAndIsActiveTrueOrderByCreatedAtAsc(
                  TENANT_ID, WORK_ORDER_ID))
          .thenReturn(List.of(consumptionWithoutMaterial));

      assertThatThrownBy(() -> recalculationService.recalculateActualCost(WORK_ORDER_ID))
          .isInstanceOf(WorkOrderDomainException.class)
          .hasMessageContaining("No consumption records with materialId found")
          .hasMessageContaining("Sprint 6");
    }
  }

  @Nested
  @DisplayName("recalculateActualCost() — happy path")
  class HappyPathTests {

    @Test
    @DisplayName("computes actual cost and writes back to WorkOrder")
    void computesActualCostAndWritesBack() {
      WorkOrder wo = buildCompletedWorkOrder();
      Batch batch = buildOutputBatch();
      UUID consumptionMaterialId = UUID.randomUUID();
      WorkOrderConsumption consumption = buildConsumption(consumptionMaterialId);
      BigDecimal computedCost = new BigDecimal("8500.000");

      when(workOrderRepository.findById(WORK_ORDER_ID)).thenReturn(Optional.of(wo));
      when(batchRepository.findFirstByTenantIdAndSourceIdAndSourceType(
              TENANT_ID, WORK_ORDER_ID, BatchSourceType.INTERNAL_PRODUCTION))
          .thenReturn(Optional.of(batch));
      when(workOrderConsumptionRepository
              .findByTenantIdAndWorkOrderIdAndIsActiveTrueOrderByCreatedAtAsc(
                  TENANT_ID, WORK_ORDER_ID))
          .thenReturn(List.of(consumption));
      when(costEnginePort.computeActualCostFromConsumptions(
              eq(TENANT_ID),
              eq(WORK_ORDER_ID),
              eq("YARN"),
              eq(MATERIAL_ID),
              eq(wo.getActualQty()),
              eq(TRADING_PARTNER_ID),
              any()))
          .thenReturn(new ComputedCostSnapshot(WORK_ORDER_ID, computedCost, "TRY"));

      WorkOrderResponse response = recalculationService.recalculateActualCost(WORK_ORDER_ID);

      assertThat(response).isNotNull();
      assertThat(wo.getActualCost()).isEqualTo(computedCost);
      assertThat(wo.getActualCostCurrency()).isEqualTo("TRY");
      verify(workOrderRepository).save(wo);
    }

    @Test
    @DisplayName("filters out consumptions without materialId and uses only valid ones")
    void filtersConsumptionsWithoutMaterialId() {
      WorkOrder wo = buildCompletedWorkOrder();
      Batch batch = buildOutputBatch();
      UUID validMaterialId = UUID.randomUUID();
      WorkOrderConsumption validConsumption = buildConsumption(validMaterialId);
      WorkOrderConsumption legacyConsumption = buildConsumption(null); // pre-Sprint 6

      when(workOrderRepository.findById(WORK_ORDER_ID)).thenReturn(Optional.of(wo));
      when(batchRepository.findFirstByTenantIdAndSourceIdAndSourceType(
              TENANT_ID, WORK_ORDER_ID, BatchSourceType.INTERNAL_PRODUCTION))
          .thenReturn(Optional.of(batch));
      when(workOrderConsumptionRepository
              .findByTenantIdAndWorkOrderIdAndIsActiveTrueOrderByCreatedAtAsc(
                  TENANT_ID, WORK_ORDER_ID))
          .thenReturn(List.of(validConsumption, legacyConsumption));
      when(costEnginePort.computeActualCostFromConsumptions(
              any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(new ComputedCostSnapshot(WORK_ORDER_ID, BigDecimal.TEN, "TRY"));

      recalculationService.recalculateActualCost(WORK_ORDER_ID);

      // Verify only 1 consumption (the valid one) was passed to cost engine
      verify(costEnginePort)
          .computeActualCostFromConsumptions(
              any(),
              any(),
              any(),
              any(),
              any(),
              any(),
              org.mockito.ArgumentMatchers.argThat(
                  (List<ConsumptionCostInput> inputs) -> inputs.size() == 1));
    }
  }
}
