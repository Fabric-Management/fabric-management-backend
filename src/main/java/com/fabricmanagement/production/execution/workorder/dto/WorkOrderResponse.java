package com.fabricmanagement.production.execution.workorder.dto;

import com.fabricmanagement.production.execution.workorder.domain.FulfillmentType;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.domain.specs.WorkOrderProductionSpecs;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

@Builder
public record WorkOrderResponse(
    UUID id,
    String uid,
    String workOrderNumber,
    UUID recipeId,
    UUID outputProductId,
    WorkOrderModuleType moduleType,
    WorkOrderProductionSpecs productionSpecs,
    UUID tradingPartnerId,
    UUID salesOrderId,
    UUID salesOrderLineId,
    FulfillmentType fulfillmentType,
    UUID fulfillmentId,
    BigDecimal plannedQty,
    String unit,
    BigDecimal unitCost,
    String currency,
    BigDecimal plannedCost,
    String plannedCostCurrency,
    WorkOrderStatus status,
    Instant deadline,
    @Schema(
            description =
                "Customer-required certification standard (e.g. GOTS, OEKO-TEX, BCI)."
                    + " Normalized to uppercase.")
        String certificationReq,
    @Schema(
            description =
                "Customer-required fiber origin country code (e.g. TR, US, EG)."
                    + " Normalized to uppercase.")
        String originReq,
    String notes,
    List<Map<String, Object>> attachments,
    BigDecimal actualQty,
    BigDecimal yieldPercentage,
    Instant completedAt,
    UUID completedBy,
    BigDecimal actualCost,
    String actualCostCurrency,
    String supplierCertificationCode,
    String supplierLicenseNo,
    LocalDate supplierLicenseValidUntil) {

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
        .outputProductId(workOrder.getOutputProductId())
        .moduleType(workOrder.getModuleType())
        .productionSpecs(workOrder.getProductionSpecs())
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
        .certificationReq(workOrder.getCertificationReq())
        .originReq(workOrder.getOriginReq())
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
