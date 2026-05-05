package com.fabricmanagement.procurement.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Üretim modülü türü — Tedarik belgelerinin (RFQ, Quote, PO) hangi üretim dalına ait olduğunu
 * belirtir.
 */
@Schema(
    description =
        "Procurement module type indicating the production branch (Fiber, Yarn, Fabric, Dye/Finishing, or Generic)")
public enum ProcurementModuleType {
  FIBER,
  YARN,
  FABRIC,
  DYE_FINISHING,

  /** Tiplendirilmemiş nesneler ve geriye dönük uyumluluk için varsayılan değer. */
  GENERIC
}
