package com.fabricmanagement.iwm.stockcount.domain;

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
@Table(name = "stock_count_line", schema = "iwm")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockCountLine extends BaseEntity {

  @Column(name = "stock_count_id", nullable = false)
  private UUID stockCountId;

  @Column(name = "material_id", nullable = false)
  private UUID materialId;

  @Column(name = "lot_number", nullable = false, length = 100)
  private String lotNumber;

  @Column(name = "goods_receipt_item_id")
  private UUID goodsReceiptItemId;

  @Column(name = "barcode", length = 100)
  private String barcode;

  @Column(name = "system_qty", nullable = false, precision = 15, scale = 3)
  private BigDecimal systemQty;

  @Column(name = "counted_qty", precision = 15, scale = 3)
  private BigDecimal countedQty;

  @Column(name = "variance", precision = 15, scale = 3)
  private BigDecimal variance;

  @Column(name = "variance_reason", columnDefinition = "TEXT")
  private String varianceReason;

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_method", nullable = false, length = 30)
  private EntryMethod entryMethod;

  @Column(name = "is_verified", nullable = false)
  private Boolean isVerified;

  public StockCountLine(
      UUID tenantId,
      UUID stockCountId,
      UUID materialId,
      String lotNumber,
      UUID goodsReceiptItemId,
      String barcode,
      BigDecimal systemQty) {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(stockCountId, "stockCountId must not be null");
    Objects.requireNonNull(materialId, "materialId must not be null");
    if (systemQty == null || systemQty.compareTo(BigDecimal.ZERO) < 0) {
      throw new IwmDomainException("systemQty must not be negative");
    }
    this.setTenantId(tenantId);
    this.stockCountId = stockCountId;
    this.materialId = materialId;
    this.lotNumber = lotNumber;
    this.goodsReceiptItemId = goodsReceiptItemId;
    this.barcode = barcode;
    this.systemQty = systemQty;
    this.entryMethod = EntryMethod.MANUAL;
    this.isVerified = false;
    this.setIsActive(true);
  }

  public void updateCount(BigDecimal countedQty, EntryMethod entryMethod) {
    Objects.requireNonNull(countedQty, "countedQty must not be null");
    this.countedQty = countedQty;
    this.variance = countedQty.subtract(this.systemQty);
    this.entryMethod = entryMethod;
  }

  public void verify(String varianceReason) {
    this.isVerified = true;
    this.varianceReason = varianceReason;
  }

  @Override
  protected String getModuleCode() {
    return "IWM-CNT";
  }
}
