package com.fabricmanagement.procurement.subcontract.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
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
 *   <li>Raw product batches are issued (PRODUCT_SENT → IWM ISSUE).
 *   <li>Subcontractor processes the product.
 *   <li>Finished goods are returned via GoodsReceipt (sourceType=SUBCONTRACT_ORDER).
 *   <li>GoodsReceipt CONFIRMED → this order moves to COMPLETED.
 *   <li>Waste = productSentQty − actualReturnedQty is recorded.
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

  /**
   * Auto-generated business key. Pre-PR-2 records use legacy SC-{YEAR}-{8-HEX} format. New records
   * use SC-{YYYYMMDD}-{NNNNN}. Both coexist; searchText substring filter is format-agnostic.
   */
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

  /** Product expected to process (input). */
  @Column(name = "product_id")
  private UUID inputProductId;

  @Enumerated(EnumType.STRING)
  @Column(name = "input_product_type", length = 30)
  private ProductType inputProductType;

  /** Finished product expected from subcontractor. */
  @Column(name = "output_product_id")
  private UUID outputProductId;

  @Enumerated(EnumType.STRING)
  @Column(name = "output_product_type", length = 30)
  private ProductType outputProductType;

  @Column(name = "expected_output_qty", precision = 15, scale = 3)
  private BigDecimal expectedOutputQty;

  @Column(name = "output_unit", length = 20)
  private String outputUnit;

  /** Quantity of raw product dispatched to subcontractor. */
  @Column(name = "product_sent_qty", precision = 15, scale = 3)
  private BigDecimal productSentQty;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  /** Quantity actually returned — populated on GoodsReceipt confirmation. */
  @Column(name = "actual_returned_qty", precision = 15, scale = 3)
  private BigDecimal actualReturnedQty;

  /**
   * Computed waste: productSentQty − actualReturnedQty. Populated when status transitions to
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
      UUID inputProductId,
      ProductType inputProductType,
      UUID outputProductId,
      ProductType outputProductType,
      BigDecimal expectedOutputQty,
      String outputUnit,
      BigDecimal productSentQty,
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
    sc.setInputProductId(inputProductId);
    sc.setInputProductType(inputProductType);
    sc.setOutputProductId(outputProductId);
    sc.setOutputProductType(outputProductType);
    sc.setExpectedOutputQty(expectedOutputQty);
    sc.setOutputUnit(outputUnit);
    sc.setProductSentQty(productSentQty);
    sc.setUnit(unit);
    sc.setAgreedUnitPrice(agreedUnitPrice);
    sc.setCurrency(currency);
    sc.setExpectedReturnDate(expectedReturnDate);
    sc.setNotes(notes);
    sc.onCreate();
    return sc;
  }
}
