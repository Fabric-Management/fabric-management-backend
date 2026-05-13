package com.fabricmanagement.iwm.rma.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.iwm.common.exception.IwmDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "rma_line", schema = "iwm")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RmaLine extends BaseEntity {

  @Column(name = "rma_id", nullable = false)
  private UUID rmaId;

  @Column(name = "sales_order_line_id", nullable = false)
  private UUID salesOrderLineId;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(name = "lot_number", nullable = false, length = 100)
  private String lotNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "defect_category", nullable = false, length = 50)
  private DefectCategory defectCategory;

  @Column(name = "qty", nullable = false, precision = 15, scale = 3)
  private BigDecimal qty;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Column(name = "destination_location_id")
  private UUID destinationLocationId;

  public RmaLine(
      UUID tenantId,
      UUID rmaId,
      UUID salesOrderLineId,
      UUID productId,
      String lotNumber,
      DefectCategory defectCategory,
      BigDecimal qty,
      String unit) {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(rmaId, "rmaId must not be null");
    Objects.requireNonNull(productId, "productId must not be null");
    Objects.requireNonNull(defectCategory, "defectCategory must not be null");
    if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IwmDomainException("qty must be positive");
    }
    this.setTenantId(tenantId);
    this.rmaId = rmaId;
    this.salesOrderLineId = salesOrderLineId;
    this.productId = productId;
    this.lotNumber = lotNumber;
    this.defectCategory = defectCategory;
    this.qty = qty;
    this.unit = unit;
    this.setIsActive(true);
  }

  public void assignDestination(UUID destinationLocationId) {
    this.destinationLocationId = destinationLocationId;
  }

  @Override
  protected String getModuleCode() {
    return "IWM-RMA";
  }
}
