package com.fabricmanagement.production.execution.goodsreceipt.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Physical goods receipt record.
 *
 * <p>Covers three source types via polymorphic FK (sourceType + sourceId):
 *
 * <ul>
 *   <li>BATCH — internal production output
 *   <li>PURCHASE_ORDER — external supplier delivery
 *   <li>SUBCONTRACT_ORDER — finished goods returned from subcontractor
 * </ul>
 *
 * <p>On CONFIRMED: IWM StockTransaction(RECEIPT) is triggered for each GoodsReceiptItem. The source
 * order (PO/SC) status is updated accordingly.
 *
 * <p>Table: {@code production.goods_receipt}
 */
@Entity
@Table(name = "goods_receipt", schema = "production")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GoodsReceipt extends BaseEntity {

  /** Auto-generated business key. Format: GR-{YEAR}-{4-digit seq}. */
  @Column(name = "receipt_number", nullable = false, length = 50)
  private String receiptNumber;

  /** Identifies the origin: BATCH, PURCHASE_ORDER, or SUBCONTRACT_ORDER. */
  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 30)
  private GoodsReceiptSourceType sourceType;

  /** Polymorphic FK to the originating record (Batch, PurchaseOrder, SubcontractOrder). */
  @Column(name = "source_id", nullable = false)
  private UUID sourceId;

  /** PO line received by this document. Set only for PURCHASE_ORDER receipts. */
  @Column(name = "source_line_id")
  private UUID sourceLineId;

  /** Supplier-provided lot/dye-batch reference copied to the materialized Batch. */
  @Column(name = "supplier_batch_code", length = 100)
  private String supplierBatchCode;

  /** User who physically received the goods on the warehouse floor. */
  @Column(name = "received_by_id", nullable = false)
  private UUID receivedById;

  /** Timestamp of physical delivery. Defaults to confirmation time if not specified. */
  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  /** Number of packages / bales / reels delivered. */
  @Column(name = "package_count", nullable = false)
  private Integer packageCount;

  /** Total gross weight of the delivery (optional, kg). */
  @Column(name = "gross_weight", precision = 15, scale = 3)
  private BigDecimal grossWeight;

  /** Total net weight of the delivery (optional, kg). */
  @Column(name = "net_weight", precision = 15, scale = 3)
  private BigDecimal netWeight;

  /** Vehicle / carrier info (plate number, courier name, etc.). */
  @Column(name = "vehicle_info", length = 255)
  private String vehicleInfo;

  /** Damage or waste notes observed during receipt. */
  @Column(name = "damage_notes", columnDefinition = "TEXT")
  private String damageNotes;

  /** Current lifecycle status. */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private GoodsReceiptStatus status;

  @Override
  protected String getModuleCode() {
    return "GR";
  }

  /**
   * Ensures the receipt is still in DRAFT state. Should be called before applying any mutations.
   *
   * @throws
   *     com.fabricmanagement.production.execution.goodsreceipt.domain.exception.GoodsReceiptDomainException
   *     if not in DRAFT
   */
  public void assertIsDraft() {
    if (this.status != GoodsReceiptStatus.DRAFT) {
      throw new com.fabricmanagement.production.execution.goodsreceipt.domain.exception
          .GoodsReceiptDomainException(
          String.format(
              "GoodsReceipt %s is %s; modifications are not allowed.",
              this.receiptNumber, this.status));
    }
  }
}
