package com.fabricmanagement.production.execution.workorder.dto;

import com.fabricmanagement.production.execution.workorder.domain.FulfillmentType;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderResponse {

  private UUID id;
  private String uid;
  private String workOrderNumber;
  private UUID recipeId;
  private UUID tradingPartnerId;
  private UUID salesOrderId;
  private UUID salesOrderLineId;
  private FulfillmentType fulfillmentType;
  private UUID fulfillmentId;
  private BigDecimal plannedQty;
  private String unit;
  private BigDecimal unitCost;
  private String currency;
  private BigDecimal plannedCost;
  private String plannedCostCurrency;
  private WorkOrderStatus status;
  private Instant deadline;
  private String notes;
  private List<Map<String, Object>> attachments;

  private BigDecimal actualQty;
  private BigDecimal yieldPercentage;
  private Instant completedAt;
  private UUID completedBy;

  private BigDecimal actualCost;
  private String actualCostCurrency;

  // Supplier Snapshot Fields
  private String supplierCertificationCode;
  private String supplierLicenseNo;
  private LocalDate supplierLicenseValidUntil;

  /**
   * Static factory — maps a WorkOrder entity to its response DTO. Single source of truth for
   * mapping; eliminates duplication across services.
   */
  public static WorkOrderResponse from(WorkOrder workOrder) {
    return WorkOrderResponse.builder()
        .id(workOrder.getId())
        .uid(workOrder.getUid())
        .workOrderNumber(workOrder.getWorkOrderNumber())
        .recipeId(workOrder.getRecipeId())
        .tradingPartnerId(workOrder.getTradingPartnerId())
        .salesOrderId(workOrder.getSalesOrderId())
        .salesOrderLineId(workOrder.getSalesOrderLineId())
        .fulfillmentType(workOrder.getFulfillmentType())
        .fulfillmentId(workOrder.getFulfillmentId())
        .plannedQty(workOrder.getPlannedQty())
        .unit(workOrder.getUnit())
        .unitCost(workOrder.getUnitCost())
        .currency(workOrder.getCurrency())
        .plannedCost(workOrder.getPlannedCost())
        .plannedCostCurrency(workOrder.getPlannedCostCurrency())
        .status(workOrder.getStatus())
        .deadline(workOrder.getDeadline())
        .notes(workOrder.getNotes())
        .attachments(workOrder.getAttachments())
        .actualQty(workOrder.getActualQty())
        .yieldPercentage(workOrder.getYieldPercentage())
        .completedAt(workOrder.getCompletedAt())
        .completedBy(workOrder.getCompletedBy())
        .actualCost(workOrder.getActualCost())
        .actualCostCurrency(workOrder.getActualCostCurrency())
        .supplierCertificationCode(workOrder.getSupplierCertificationCode())
        .supplierLicenseNo(workOrder.getSupplierLicenseNo())
        .supplierLicenseValidUntil(workOrder.getSupplierLicenseValidUntil())
        .build();
  }
}
