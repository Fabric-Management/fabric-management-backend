package com.fabricmanagement.production.execution.inventory.domain;

/**
 * Types of inventory stock movements.
 *
 * <pre>
 * RECEIPT      : Incoming goods from supplier or production output (stok girişi)
 * CONSUMPTION  : Used in production (üretim tüketimi)
 * WASTE        : Production loss / fire / telef (üretim firesi)
 * ADJUSTMENT   : Inventory count correction — can be positive or negative (sayım düzeltme)
 * TRANSFER     : Warehouse-to-warehouse move (depo transferi)
 * RETURN       : Return to supplier (tedarikçi iadesi)
 * SAMPLE       : Taken for laboratory testing (numune)
 * </pre>
 */
public enum InventoryTransactionType {
  RECEIPT,
  CONSUMPTION,
  WASTE,
  ADJUSTMENT,
  TRANSFER,
  RETURN,
  SAMPLE
}
