package com.fabricmanagement.production.execution.goodsreceipt.infra.repository;

import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceipt;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, UUID> {

  /** All receipts for a given source (e.g. all receipts for one PurchaseOrder). */
  List<GoodsReceipt> findBySourceTypeAndSourceIdAndIsActiveTrue(
      GoodsReceiptSourceType sourceType, UUID sourceId);

  /** Pending receipts (DRAFT) for a given source — used in warehouse workflow. */
  List<GoodsReceipt> findBySourceTypeAndSourceIdAndStatusAndIsActiveTrue(
      GoodsReceiptSourceType sourceType, UUID sourceId, GoodsReceiptStatus status);

  /** Check if a receipt number already exists (collision guard). */
  boolean existsByReceiptNumberAndIsActiveTrue(String receiptNumber);
}
