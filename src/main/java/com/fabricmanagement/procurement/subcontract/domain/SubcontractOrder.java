package com.fabricmanagement.procurement.subcontract.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A subcontract order issued to a third-party processor (fason).
 *
 * <p>Flow:
 *
 * <ol>
 *   <li>Raw material batches are issued (MATERIAL_SENT → IWM ISSUE).
 *   <li>Subcontractor processes the material.
 *   <li>Finished goods are returned via GoodsReceipt (sourceType=SUBCONTRACT_ORDER).
 *   <li>GoodsReceipt CONFIRMED → this order moves to COMPLETED.
 *   <li>Waste = materialSentQty − actualReturnedQty is recorded.
 * </ol>
 *
 * <p>Table: {@code procurement.subcontract_order}
 */
@Entity
@Table(name = "subcontract_order", schema = "procurement")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SubcontractOrder extends BaseEntity {

  /** Auto-generated business key. Format: SC-{YEAR}-{8-char}. */
  @Column(name = "sc_number", nullable = false, length = 50)
  private String scNumber;

  /** The internal production order this SC is fulfilling. */
  @Column(name = "work_order_id", nullable = false)
  private UUID workOrderId;

  /** FK → TradingPartner — the subcontractor (fason firması). */
  @Column(name = "trading_partner_id", nullable = false)
  private UUID tradingPartnerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 25)
  private SubcontractOrderStatus status;

  /** Material expected to process. */
  @Column(name = "material_id")
  private UUID materialId;

  /** Quantity of raw material dispatched to subcontractor. */
  @Column(name = "material_sent_qty", precision = 15, scale = 3)
  private BigDecimal materialSentQty;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  /** Quantity actually returned — populated on GoodsReceipt confirmation. */
  @Column(name = "actual_returned_qty", precision = 15, scale = 3)
  private BigDecimal actualReturnedQty;

  /**
   * Computed waste: materialSentQty − actualReturnedQty. Populated when status transitions to
   * COMPLETED.
   */
  @Column(name = "waste_qty", precision = 15, scale = 3)
  private BigDecimal wasteQty;

  @Column(name = "agreed_unit_price", precision = 18, scale = 4)
  private BigDecimal agreedUnitPrice;

  @Column(name = "currency", length = 3)
  private String currency;

  @Column(name = "expected_return_date")
  private LocalDate expectedReturnDate;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Override
  protected String getModuleCode() {
    return "SC";
  }
}
