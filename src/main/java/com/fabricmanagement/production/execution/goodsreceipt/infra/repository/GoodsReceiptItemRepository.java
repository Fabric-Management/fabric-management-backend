package com.fabricmanagement.production.execution.goodsreceipt.infra.repository;

import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptItem;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoodsReceiptItemRepository extends JpaRepository<GoodsReceiptItem, UUID> {

  /** All items for a receipt, ordered by sequence number. */
  List<GoodsReceiptItem> findByGoodsReceiptIdAndIsActiveTrueOrderBySequenceNoAsc(
      UUID goodsReceiptId);

  /** Barcode uniqueness check. */
  boolean existsByBarcodeAndIsActiveTrue(String barcode);

  /**
   * Returns one aggregate bucket per PO line and length unit. The adapter performs the final
   * dimension-aware conversion into each PO line's requested unit.
   */
  @Query(
      """
      select
        receipt.sourceLineId as sourceLineId,
        item.lengthUnit as lengthUnit,
        sum(item.netWeight) as netWeightTotal,
        sum(item.length) as lengthTotal,
        count(item.id) as itemCount,
        count(item.netWeight) as netWeightItemCount,
        count(item.length) as lengthMeasureItemCount
      from GoodsReceipt receipt, GoodsReceiptItem item
      where item.goodsReceiptId = receipt.id
        and receipt.tenantId = :tenantId
        and item.tenantId = :tenantId
        and receipt.sourceType = :sourceType
        and receipt.sourceId = :sourceId
        and receipt.status = :status
        and receipt.isActive = true
        and item.isActive = true
      group by receipt.sourceLineId, item.lengthUnit
      """)
  List<PoReceiptMeasureBucket> sumConfirmedPoReceiptMeasures(
      @Param("tenantId") UUID tenantId,
      @Param("sourceType") GoodsReceiptSourceType sourceType,
      @Param("sourceId") UUID sourceId,
      @Param("status") GoodsReceiptStatus status);

  interface PoReceiptMeasureBucket {
    UUID getSourceLineId();

    String getLengthUnit();

    BigDecimal getNetWeightTotal();

    BigDecimal getLengthTotal();

    long getItemCount();

    long getNetWeightItemCount();

    long getLengthMeasureItemCount();
  }
}
