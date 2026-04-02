package com.fabricmanagement.procurement.subcontract.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubcontractOrder extends BaseEntity {

  /** Auto-generated business key. Format: SC-{YEAR}-{8-char}. */
  @Column(name = "sc_number", nullable = false, length = 50)
  private String scNumber;

  /** The internal production order this SC is fulfilling. */
  @Column(name = "work_order_id", nullable = false)
  private UUID workOrderId;

  @Column(name = "batch_id")
  private UUID batchId;

  /** FK → TradingPartner — the subcontractor (fason firması). */
  @Column(name = "trading_partner_id", nullable = false)
  private UUID tradingPartnerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 25)
  private SubcontractOrderStatus status;

  /** Material expected to process (input). */
  @Column(name = "material_id")
  private UUID inputMaterialId;

  @Enumerated(EnumType.STRING)
  @Column(name = "input_material_type", length = 30)
  private MaterialType inputMaterialType;

  /** Finished material expected from subcontractor. */
  @Column(name = "output_material_id")
  private UUID outputMaterialId;

  @Enumerated(EnumType.STRING)
  @Column(name = "output_material_type", length = 30)
  private MaterialType outputMaterialType;

  @Column(name = "expected_output_qty", precision = 15, scale = 3)
  private BigDecimal expectedOutputQty;

  @Column(name = "output_unit", length = 20)
  private String outputUnit;

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

  public static SubcontractOrder create(
      UUID tenantId,
      String scNumber,
      UUID workOrderId,
      UUID batchId,
      UUID tradingPartnerId,
      UUID inputMaterialId,
      MaterialType inputMaterialType,
      UUID outputMaterialId,
      MaterialType outputMaterialType,
      BigDecimal expectedOutputQty,
      String outputUnit,
      BigDecimal materialSentQty,
      String unit,
      BigDecimal agreedUnitPrice,
      String currency,
      LocalDate expectedReturnDate,
      String notes) {
    SubcontractOrder sc = new SubcontractOrder();
    sc.setTenantId(tenantId);
    sc.setScNumber(scNumber);
    sc.setWorkOrderId(workOrderId);
    sc.setBatchId(batchId);
    sc.setTradingPartnerId(tradingPartnerId);
    sc.setStatus(SubcontractOrderStatus.DRAFT);
    sc.setInputMaterialId(inputMaterialId);
    sc.setInputMaterialType(inputMaterialType);
    sc.setOutputMaterialId(outputMaterialId);
    sc.setOutputMaterialType(outputMaterialType);
    sc.setExpectedOutputQty(expectedOutputQty);
    sc.setOutputUnit(outputUnit);
    sc.setMaterialSentQty(materialSentQty);
    sc.setUnit(unit);
    sc.setAgreedUnitPrice(agreedUnitPrice);
    sc.setCurrency(currency);
    sc.setExpectedReturnDate(expectedReturnDate);
    sc.setNotes(notes);
    sc.onCreate();
    return sc;
  }
}
