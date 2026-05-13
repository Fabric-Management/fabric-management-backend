package com.fabricmanagement.iwm.adjustment.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.iwm.common.exception.IwmDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock_adjustment_request", schema = "iwm")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockAdjustmentRequest extends BaseEntity {

  @Column(name = "request_number", nullable = false, length = 100)
  private String requestNumber;

  @Column(name = "location_id", nullable = false)
  private UUID locationId;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(name = "lot_number", nullable = false, length = 100)
  private String lotNumber;

  @Column(name = "qty_adjustment", nullable = false, precision = 15, scale = 3)
  private BigDecimal qtyAdjustment;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Column(name = "reason", nullable = false, length = 200)
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private StockAdjustmentStatus status;

  @Column(name = "approved_at")
  private OffsetDateTime approvedAt;

  @Column(name = "approved_by")
  private UUID approvedBy;

  @Column(name = "rejected_at")
  private OffsetDateTime rejectedAt;

  @Column(name = "rejected_by")
  private UUID rejectedBy;

  public StockAdjustmentRequest(
      UUID tenantId,
      String requestNumber,
      UUID locationId,
      UUID productId,
      String lotNumber,
      BigDecimal qtyAdjustment,
      String unit,
      String reason) {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(locationId, "locationId must not be null");
    Objects.requireNonNull(productId, "productId must not be null");
    if (requestNumber == null || requestNumber.isBlank()) {
      throw new IwmDomainException("requestNumber must not be blank");
    }
    if (qtyAdjustment == null || qtyAdjustment.compareTo(BigDecimal.ZERO) == 0) {
      throw new IwmDomainException("qtyAdjustment must not be zero");
    }
    if (reason == null || reason.isBlank()) {
      throw new IwmDomainException("reason must not be blank");
    }
    this.setTenantId(tenantId);
    this.requestNumber = requestNumber;
    this.locationId = locationId;
    this.productId = productId;
    this.lotNumber = lotNumber;
    this.qtyAdjustment = qtyAdjustment;
    this.unit = unit;
    this.reason = reason;
    this.status = StockAdjustmentStatus.DRAFT;
    this.setIsActive(true);
  }

  public void submitForApproval() {
    if (this.status != StockAdjustmentStatus.DRAFT) {
      throw new IwmDomainException("Only DRAFT requests can be submitted, current: " + this.status);
    }
    this.status = StockAdjustmentStatus.PENDING_APPROVAL;
  }

  public void approve(UUID approverId) {
    if (this.status != StockAdjustmentStatus.PENDING_APPROVAL) {
      throw new IwmDomainException(
          "Only PENDING_APPROVAL requests can be approved, current: " + this.status);
    }
    Objects.requireNonNull(approverId, "approverId must not be null");
    this.status = StockAdjustmentStatus.APPROVED;
    this.approvedBy = approverId;
    this.approvedAt = OffsetDateTime.now();
  }

  public void reject(UUID rejectorId) {
    if (this.status != StockAdjustmentStatus.PENDING_APPROVAL) {
      throw new IwmDomainException(
          "Only PENDING_APPROVAL requests can be rejected, current: " + this.status);
    }
    Objects.requireNonNull(rejectorId, "rejectorId must not be null");
    this.status = StockAdjustmentStatus.REJECTED;
    this.rejectedBy = rejectorId;
    this.rejectedAt = OffsetDateTime.now();
  }

  @Override
  protected String getModuleCode() {
    return "IWM-ADJ";
  }
}
