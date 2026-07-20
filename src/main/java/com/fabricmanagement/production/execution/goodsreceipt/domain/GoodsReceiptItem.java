package com.fabricmanagement.production.execution.goodsreceipt.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single physical unit (bale, reel, roll, carton) within a GoodsReceipt.
 *
 * <p>Barcode is auto-generated based on source type:
 *
 * <ul>
 *   <li>Batch: {@code BCH-{batchNumber}-{seq}}
 *   <li>PurchaseOrder: {@code PO-{poNumber}-{seq}}
 *   <li>SubcontractOrder: {@code SC-{scNumber}-{seq}}
 * </ul>
 *
 * <p>Table: {@code production.goods_receipt_item}
 */
@Entity
@Table(name = "goods_receipt_item", schema = "production")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GoodsReceiptItem extends BaseEntity {

  /** FK to the parent GoodsReceipt. */
  @Column(name = "goods_receipt_id", nullable = false)
  private UUID goodsReceiptId;

  /** 1-based sequential position within this receipt. Used for barcode suffix (e.g. -001, -002). */
  @Column(name = "sequence_no", nullable = false)
  private Integer sequenceNo;

  /** Auto-generated scan barcode. Format depends on the parent GoodsReceipt.sourceType. */
  @Column(name = "barcode", nullable = false, length = 100)
  private String barcode;

  /** Optional manufacturer or supplier serial number. */
  @Column(name = "serial_number", length = 100)
  private String serialNumber;

  /** Net weight of this specific unit (kg). */
  @Column(name = "net_weight", nullable = false, precision = 15, scale = 3)
  private BigDecimal netWeight;

  /** Gross weight of this specific unit (kg). Optional. */
  @Column(name = "gross_weight", precision = 15, scale = 3)
  private BigDecimal grossWeight;

  /** Optional physical length; required for length-primary purchased products. */
  @Column(name = "length", precision = 15, scale = 3)
  private BigDecimal length;

  /** Metric unit for {@link #length}. */
  @Column(name = "length_unit", length = 10)
  private String lengthUnit;

  /** Item-level notes (damage, condition, labelling issues). */
  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Override
  protected String getModuleCode() {
    return "GRI";
  }
}
