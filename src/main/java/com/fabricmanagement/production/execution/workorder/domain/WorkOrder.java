package com.fabricmanagement.production.execution.workorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "prod_work_order", schema = "production")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkOrder extends BaseEntity {

  @Column(name = "work_order_number", nullable = false, length = 100)
  private String workOrderNumber;

  @Column(name = "recipe_id")
  private UUID recipeId;

  @Column(name = "trading_partner_id")
  private UUID tradingPartnerId;

  @Column(name = "sales_order_id")
  private UUID salesOrderId;

  @Column(name = "sales_order_line_id")
  private UUID salesOrderLineId;

  @Column(name = "product_code", length = 100)
  private String productCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "fulfillment_type", nullable = false, length = 20)
  private FulfillmentType fulfillmentType;

  @Column(name = "fulfillment_id")
  private UUID fulfillmentId;

  public static WorkOrder createFromSalesOrderLine(
      UUID tenantId,
      UUID salesOrderId,
      UUID salesOrderLineId,
      String productCode,
      BigDecimal quantity,
      String unit,
      LocalDate requestedDeliveryDate,
      String workOrderNumber) {

    WorkOrder w =
        WorkOrder.builder()
            .salesOrderId(salesOrderId)
            .salesOrderLineId(salesOrderLineId)
            .productCode(productCode)
            .plannedQty(quantity)
            .unit(unit)
            .deadline(
                requestedDeliveryDate != null
                    ? requestedDeliveryDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
                    : null)
            .status(WorkOrderStatus.DRAFT)
            .workOrderNumber(workOrderNumber)
            .fulfillmentType(FulfillmentType.INTERNAL)
            .build();

    w.setTenantId(tenantId);
    return w;
  }

  @Column(name = "planned_qty", nullable = false, precision = 15, scale = 3)
  private BigDecimal plannedQty;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Column(name = "unit_cost", precision = 15, scale = 3)
  private BigDecimal unitCost;

  @Column(name = "currency", length = 3)
  private String currency;

  @Column(name = "planned_cost", precision = 15, scale = 3)
  private BigDecimal plannedCost;

  @Column(name = "planned_cost_currency", length = 3)
  private String plannedCostCurrency;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private WorkOrderStatus status;

  @Column(name = "deadline")
  private Instant deadline;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @org.hibernate.annotations.Type(io.hypersistence.utils.hibernate.type.json.JsonType.class)
  @Column(name = "attachments", columnDefinition = "jsonb")
  @Builder.Default
  private List<Map<String, Object>> attachments = List.of();

  // --- Supplier Snapshot Fields --- //

  @Column(name = "supplier_certification_code", length = 100)
  private String supplierCertificationCode;

  @Column(name = "supplier_license_no", length = 100)
  private String supplierLicenseNo;

  @Column(name = "supplier_license_valid_until")
  private LocalDate supplierLicenseValidUntil;

  @Override
  protected String getModuleCode() {
    return "WO";
  }
}
