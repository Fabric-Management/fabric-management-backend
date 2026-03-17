package com.fabricmanagement.production.execution.goodsreceipt.domain;

/**
 * Identifies the origin of a GoodsReceipt.
 *
 * <p>GoodsReceipt is a polymorphic form — the same entity covers:
 *
 * <ul>
 *   <li>Internal production output (optional — QC-driven flow may skip it)
 *   <li>Purchase order delivery (mandatory for external procurement)
 *   <li>Subcontract order return (mandatory for subcontracted production)
 * </ul>
 */
public enum GoodsReceiptSourceType {

  /** Goods received from internal production. sourceId → Batch.id */
  BATCH,

  /**
   * Goods received from an external supplier against a PurchaseOrder. sourceId → PurchaseOrder.id
   */
  PURCHASE_ORDER,

  /** Finished goods returned from a subcontractor. sourceId → SubcontractOrder.id */
  SUBCONTRACT_ORDER
}
