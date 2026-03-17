package com.fabricmanagement.procurement.quote.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.util.UUID;
import lombok.Getter;

/** Tedarikçi teklif verdi — NORMAL. */
@Getter
public class SupplierQuoteReceivedEvent extends DomainEvent {

  private final UUID quoteId;
  private final UUID rfqId;
  private final UUID supplierId;
  private final String supplierName;

  public SupplierQuoteReceivedEvent(
      UUID tenantId, UUID quoteId, UUID rfqId, UUID supplierId, String supplierName) {
    super(tenantId, "SUPPLIER_QUOTE_RECEIVED");
    this.quoteId = quoteId;
    this.rfqId = rfqId;
    this.supplierId = supplierId;
    this.supplierName = supplierName;
  }
}
