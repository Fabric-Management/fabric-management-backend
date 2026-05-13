package com.fabricmanagement.iwm.reservation.domain;

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
@Table(name = "stock_reservation", schema = "iwm")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReservation extends BaseEntity {

  @Column(name = "sales_order_line_id", nullable = false)
  private UUID salesOrderLineId;

  @Column(name = "location_id", nullable = false)
  private UUID locationId;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(name = "lot_number", nullable = false, length = 100)
  private String lotNumber;

  @Column(name = "goods_receipt_item_id")
  private UUID goodsReceiptItemId;

  @Column(name = "qty_reserved", nullable = false, precision = 15, scale = 3)
  private BigDecimal qtyReserved;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private StockReservationStatus status;

  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;

  public StockReservation(
      UUID tenantId,
      UUID salesOrderLineId,
      UUID locationId,
      UUID productId,
      String lotNumber,
      UUID goodsReceiptItemId,
      BigDecimal qtyReserved,
      OffsetDateTime expiresAt) {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(salesOrderLineId, "salesOrderLineId must not be null");
    Objects.requireNonNull(locationId, "locationId must not be null");
    Objects.requireNonNull(productId, "productId must not be null");
    if (lotNumber == null || lotNumber.isBlank()) {
      throw new IwmDomainException("lotNumber must not be blank");
    }
    if (qtyReserved == null || qtyReserved.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IwmDomainException("qtyReserved must be positive");
    }
    this.setTenantId(tenantId);
    this.salesOrderLineId = salesOrderLineId;
    this.locationId = locationId;
    this.productId = productId;
    this.lotNumber = lotNumber;
    this.goodsReceiptItemId = goodsReceiptItemId;
    this.qtyReserved = qtyReserved;
    this.status = StockReservationStatus.ACTIVE;
    this.expiresAt = expiresAt;
    this.setIsActive(true);
  }

  public void release() {
    if (this.status != StockReservationStatus.ACTIVE) {
      throw new IwmDomainException("Only active reservations can be released");
    }
    this.status = StockReservationStatus.RELEASED;
  }

  public void convert() {
    if (this.status != StockReservationStatus.ACTIVE) {
      throw new IwmDomainException("Only active reservations can be converted");
    }
    this.status = StockReservationStatus.CONVERTED;
  }

  @Override
  protected String getModuleCode() {
    return "IWM-RES";
  }
}
