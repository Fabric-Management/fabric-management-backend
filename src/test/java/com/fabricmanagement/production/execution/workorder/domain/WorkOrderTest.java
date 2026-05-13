package com.fabricmanagement.production.execution.workorder.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.production.execution.workorder.domain.exception.WorkOrderDomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WorkOrder — domain entity")
class WorkOrderTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  private WorkOrder buildWorkOrder(WorkOrderStatus status) {
    WorkOrder wo =
        WorkOrder.builder()
            .workOrderNumber("WO-TEST-001")
            .status(status)
            .outputProductId(UUID.randomUUID())
            .moduleType(WorkOrderModuleType.GENERIC)
            .plannedQty(new BigDecimal("1000.000"))
            .unit("KG")
            .tradingPartnerId(UUID.randomUUID())
            .fulfillmentType(FulfillmentType.INTERNAL)
            .build();
    wo.setId(UUID.randomUUID());
    wo.setTenantId(TENANT_ID);
    wo.setIsActive(true);
    return wo;
  }

  // ─── complete() ───────────────────────────────────────────────

  @Nested
  @DisplayName("complete()")
  class CompleteTests {

    @Test
    @DisplayName("transitions IN_PROGRESS to COMPLETED with all fields set")
    void completesFromInProgress() {
      WorkOrder wo = buildWorkOrder(WorkOrderStatus.IN_PROGRESS);
      UUID completedBy = UUID.randomUUID();
      BigDecimal actualQty = new BigDecimal("950.000");
      BigDecimal yieldPct = new BigDecimal("95.00");

      wo.complete(actualQty, yieldPct, completedBy);

      assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.COMPLETED);
      assertThat(wo.getActualQty()).isEqualByComparingTo(actualQty);
      assertThat(wo.getYieldPercentage()).isEqualByComparingTo(yieldPct);
      assertThat(wo.getCompletedBy()).isEqualTo(completedBy);
      assertThat(wo.getCompletedAt()).isNotNull();
      assertThat(wo.getCompletedAt()).isBefore(Instant.now().plusSeconds(1));
    }

    @Test
    @DisplayName("throws when completing from DRAFT")
    void cannotCompleteFromDraft() {
      WorkOrder wo = buildWorkOrder(WorkOrderStatus.DRAFT);

      assertThatThrownBy(() -> wo.complete(BigDecimal.TEN, BigDecimal.TEN, UUID.randomUUID()))
          .isInstanceOf(WorkOrderDomainException.class)
          .hasMessageContaining("Cannot complete WO in status: DRAFT");
    }

    @Test
    @DisplayName("throws when completing from APPROVED")
    void cannotCompleteFromApproved() {
      WorkOrder wo = buildWorkOrder(WorkOrderStatus.APPROVED);

      assertThatThrownBy(() -> wo.complete(BigDecimal.TEN, BigDecimal.TEN, UUID.randomUUID()))
          .isInstanceOf(WorkOrderDomainException.class)
          .hasMessageContaining("Cannot complete WO in status: APPROVED");
    }

    @Test
    @DisplayName("throws when completing an already COMPLETED WorkOrder")
    void cannotCompleteAlreadyCompleted() {
      WorkOrder wo = buildWorkOrder(WorkOrderStatus.IN_PROGRESS);
      wo.complete(BigDecimal.TEN, BigDecimal.TEN, UUID.randomUUID());

      assertThatThrownBy(() -> wo.complete(BigDecimal.ONE, BigDecimal.ONE, UUID.randomUUID()))
          .isInstanceOf(WorkOrderDomainException.class)
          .hasMessageContaining("Cannot complete WO in status: COMPLETED");
    }
  }

  // ─── updatePlannedCost() ──────────────────────────────────────

  @Nested
  @DisplayName("updatePlannedCost()")
  class UpdatePlannedCostTests {

    @Test
    @DisplayName("sets plannedCost and plannedCostCurrency")
    void setsPlannedCostFields() {
      WorkOrder wo = buildWorkOrder(WorkOrderStatus.APPROVED);
      BigDecimal cost = new BigDecimal("5250.500");

      wo.updatePlannedCost(cost, "TRY");

      assertThat(wo.getPlannedCost()).isEqualByComparingTo(cost);
      assertThat(wo.getPlannedCostCurrency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("accepts zero as valid planned cost")
    void acceptsZeroCost() {
      WorkOrder wo = buildWorkOrder(WorkOrderStatus.APPROVED);

      wo.updatePlannedCost(BigDecimal.ZERO, "USD");

      assertThat(wo.getPlannedCost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("throws when planned cost is null")
    void rejectsNullCost() {
      WorkOrder wo = buildWorkOrder(WorkOrderStatus.APPROVED);

      assertThatThrownBy(() -> wo.updatePlannedCost(null, "TRY"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Planned cost must be non-negative");
    }

    @Test
    @DisplayName("throws when planned cost is negative")
    void rejectsNegativeCost() {
      WorkOrder wo = buildWorkOrder(WorkOrderStatus.APPROVED);

      assertThatThrownBy(() -> wo.updatePlannedCost(new BigDecimal("-100"), "TRY"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Planned cost must be non-negative");
    }

    @Test
    @DisplayName("overwrites previous planned cost (idempotent)")
    void overwritesPreviousValue() {
      WorkOrder wo = buildWorkOrder(WorkOrderStatus.APPROVED);
      wo.updatePlannedCost(new BigDecimal("1000"), "TRY");
      wo.updatePlannedCost(new BigDecimal("2000"), "USD");

      assertThat(wo.getPlannedCost()).isEqualByComparingTo(new BigDecimal("2000"));
      assertThat(wo.getPlannedCostCurrency()).isEqualTo("USD");
    }
  }

  // ─── updateActualCost() ───────────────────────────────────────

  @Nested
  @DisplayName("updateActualCost()")
  class UpdateActualCostTests {

    @Test
    @DisplayName("sets actualCost and actualCostCurrency")
    void setsActualCostFields() {
      WorkOrder wo = buildWorkOrder(WorkOrderStatus.COMPLETED);
      BigDecimal cost = new BigDecimal("8500.000");

      wo.updateActualCost(cost, "TRY");

      assertThat(wo.getActualCost()).isEqualByComparingTo(cost);
      assertThat(wo.getActualCostCurrency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("allows null actual cost (clearing scenario)")
    void allowsNullActualCost() {
      WorkOrder wo = buildWorkOrder(WorkOrderStatus.COMPLETED);
      wo.updateActualCost(new BigDecimal("5000"), "TRY");

      wo.updateActualCost(null, null);

      assertThat(wo.getActualCost()).isNull();
      assertThat(wo.getActualCostCurrency()).isNull();
    }
  }

  // ─── createFromSalesOrderLine() ───────────────────────────────

  @Nested
  @DisplayName("createFromSalesOrderLine()")
  class FactoryMethodTests {

    @Test
    @DisplayName("creates WorkOrder in DRAFT status with correct fields")
    void createsInDraftStatus() {
      UUID tenantId = UUID.randomUUID();
      UUID salesOrderId = UUID.randomUUID();
      UUID lineId = UUID.randomUUID();

      WorkOrder wo =
          WorkOrder.createFromSalesOrderLine(
              tenantId,
              salesOrderId,
              lineId,
              "YARN-NE30",
              new BigDecimal("500.000"),
              "KG",
              null,
              "WO-2026-001");

      assertThat(wo.getTenantId()).isEqualTo(tenantId);
      assertThat(wo.getSalesOrderId()).isEqualTo(salesOrderId);
      assertThat(wo.getSalesOrderLineId()).isEqualTo(lineId);
      assertThat(wo.getProductCode()).isEqualTo("YARN-NE30");
      assertThat(wo.getPlannedQty()).isEqualByComparingTo(new BigDecimal("500.000"));
      assertThat(wo.getUnit()).isEqualTo("KG");
      assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.DRAFT);
      assertThat(wo.getWorkOrderNumber()).isEqualTo("WO-2026-001");
      assertThat(wo.getFulfillmentType()).isEqualTo(FulfillmentType.INTERNAL);
    }

    @Test
    @DisplayName("sets deadline from requestedDeliveryDate")
    void setsDeadlineFromDeliveryDate() {
      WorkOrder wo =
          WorkOrder.createFromSalesOrderLine(
              UUID.randomUUID(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              "FABRIC-001",
              BigDecimal.TEN,
              "M",
              java.time.LocalDate.of(2026, 6, 15),
              "WO-2026-002");

      assertThat(wo.getDeadline()).isNotNull();
      assertThat(wo.getDeadline().toString()).startsWith("2026-06-15");
    }

    @Test
    @DisplayName("handles null delivery date gracefully")
    void handlesNullDeliveryDate() {
      WorkOrder wo =
          WorkOrder.createFromSalesOrderLine(
              UUID.randomUUID(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              "FABRIC-001",
              BigDecimal.TEN,
              "M",
              null,
              "WO-2026-003");

      assertThat(wo.getDeadline()).isNull();
    }
  }
}
