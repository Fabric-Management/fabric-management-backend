package com.fabricmanagement.procurement.purchaseorder.domain;

/**
 * Üretim modülü türü — PO hangi üretim dalına ait. RFQ akışında SupplierRFQModuleType'tan
 * map'lenir.
 */
public enum PurchaseOrderModuleType {
  FIBER,
  YARN,
  FABRIC,
  DYE_FINISHING,

  /** Tiplendirilmemiş PO'lar ve geriye dönük uyumluluk için varsayılan değer. */
  GENERIC
}
