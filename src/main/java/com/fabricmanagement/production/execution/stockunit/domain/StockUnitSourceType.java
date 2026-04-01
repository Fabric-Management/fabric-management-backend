package com.fabricmanagement.production.execution.stockunit.domain;

/**
 * Origin source that created a {@link StockUnit}.
 *
 * <p>Every stock unit must record where it came from for full traceability. The {@code sourceId}
 * field on StockUnit is a polymorphic FK pointing to the originating record, interpreted based on
 * this enum.
 *
 * <ul>
 *   <li>{@link #GOODS_RECEIPT} → sourceId = {@code GoodsReceiptItem.id} (external supplier delivery
 *       via PO or subcontract return via SC)
 *   <li>{@link #PRODUCTION} → sourceId = {@code ProductionOutput.id} (internal production line
 *       output)
 *   <li>{@link #SPLIT} → sourceId = original {@code StockUnit.id} (a stock unit was split into
 *       smaller units)
 *   <li>{@link #RETURN} → sourceId = {@code RMA.id} or source reference (customer return or
 *       internal correction)
 * </ul>
 */
public enum StockUnitSourceType {

  /** Created from an external goods receipt (PO delivery, subcontract return). */
  GOODS_RECEIPT,

  /** Created from internal production output. */
  PRODUCTION,

  /** Created by splitting an existing stock unit into smaller units. */
  SPLIT,

  /** Created from a customer return or internal correction. */
  RETURN
}
