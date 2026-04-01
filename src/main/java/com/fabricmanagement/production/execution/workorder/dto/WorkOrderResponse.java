package com.fabricmanagement.production.execution.workorder.dto;

import com.fabricmanagement.production.execution.workorder.domain.FulfillmentType;
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
}
