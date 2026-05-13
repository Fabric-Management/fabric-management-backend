package com.fabricmanagement.iwm.transfer.domain;

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
@Table(name = "stock_transfer", schema = "iwm")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockTransfer extends BaseEntity {

  @Column(name = "transfer_number", nullable = false, length = 100)
  private String transferNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "transfer_type", nullable = false, length = 30)
  private StockTransferType transferType;

  @Column(name = "from_location_id", nullable = false)
  private UUID fromLocationId;

  @Column(name = "to_location_id", nullable = false)
  private UUID toLocationId;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(name = "lot_number", nullable = false, length = 100)
  private String lotNumber;

  @Column(name = "qty", nullable = false, precision = 15, scale = 3)
  private BigDecimal qty;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private StockTransferStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", length = 30)
  private StockTransferSourceType sourceType;

  @Column(name = "source_id")
  private UUID sourceId;

  @Column(name = "dispatched_at")
  private OffsetDateTime dispatchedAt;

  @Column(name = "received_at")
  private OffsetDateTime receivedAt;

  @Column(name = "vehicle_info", length = 255)
  private String vehicleInfo;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  public StockTransfer(
      UUID tenantId,
      String transferNumber,
      StockTransferType transferType,
      UUID fromLocationId,
      UUID toLocationId,
      UUID productId,
      String lotNumber,
      BigDecimal qty,
      String unit,
      StockTransferSourceType sourceType,
      UUID sourceId,
      String notes) {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(fromLocationId, "fromLocationId must not be null");
    Objects.requireNonNull(toLocationId, "toLocationId must not be null");
    Objects.requireNonNull(productId, "productId must not be null");
    Objects.requireNonNull(transferType, "transferType must not be null");
    if (fromLocationId.equals(toLocationId)) {
      throw new IwmDomainException("fromLocationId and toLocationId must be different");
    }
    if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IwmDomainException("Transfer qty must be positive");
    }
    if (transferNumber == null || transferNumber.isBlank()) {
      throw new IwmDomainException("transferNumber must not be blank");
    }
    this.setTenantId(tenantId);
    this.transferNumber = transferNumber;
    this.transferType = transferType;
    this.fromLocationId = fromLocationId;
    this.toLocationId = toLocationId;
    this.productId = productId;
    this.lotNumber = lotNumber;
    this.qty = qty;
    this.unit = unit;
    this.sourceType = sourceType;
    this.sourceId = sourceId;
    this.notes = notes;
    this.status = StockTransferStatus.DRAFT;
    this.setIsActive(true);
  }

  public void dispatch(String vehicleInfo) {
    if (this.status != StockTransferStatus.DRAFT) {
      throw new IwmDomainException("Only DRAFT transfers can be dispatched, current: " + status);
    }
    this.vehicleInfo = vehicleInfo;
    this.status = StockTransferStatus.IN_TRANSIT;
    this.dispatchedAt = OffsetDateTime.now();
  }

  /**
   * Transfer'ı tamamlar. Yalnızca IN_TRANSIT durumundaki transferler tamamlanabilir. Kısa mesafeli
   * (internal) transferlerde dispatch aşamasının atlanmasını istiyorsanız, önce {@link
   * #dispatch(String)} çağrılmalıdır (CR-10-09).
   */
  public void receive() {
    if (this.status != StockTransferStatus.IN_TRANSIT) {
      throw new IwmDomainException("Only IN_TRANSIT transfers can be received, current: " + status);
    }
    this.status = StockTransferStatus.COMPLETED;
    this.receivedAt = OffsetDateTime.now();
  }

  @Override
  protected String getModuleCode() {
    return "IWM-TRF";
  }
}
