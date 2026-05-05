package com.fabricmanagement.procurement.rfq.domain;

/** Üretim modülü türü — RFQ hangi üretim dalına ait. */
public enum SupplierRFQModuleType {
  FIBER,
  YARN,
  FABRIC,
  DYE_FINISHING,

  /** Tiplendirilmemiş RFQ'lar ve geriye dönük uyumluluk için varsayılan değer. */
  GENERIC
}
