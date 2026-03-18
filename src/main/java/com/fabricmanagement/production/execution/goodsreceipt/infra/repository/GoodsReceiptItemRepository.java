package com.fabricmanagement.production.execution.goodsreceipt.infra.repository;

import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsReceiptItemRepository extends JpaRepository<GoodsReceiptItem, UUID> {

  /** All items for a receipt, ordered by sequence number. */
  List<GoodsReceiptItem> findByGoodsReceiptIdAndIsActiveTrueOrderBySequenceNoAsc(
      UUID goodsReceiptId);

  /** Barcode uniqueness check. */
  boolean existsByBarcodeAndIsActiveTrue(String barcode);
}
