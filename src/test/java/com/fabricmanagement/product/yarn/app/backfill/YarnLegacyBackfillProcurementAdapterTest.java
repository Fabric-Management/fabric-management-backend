package com.fabricmanagement.product.yarn.app.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.procurement.purchaseorder.app.adapter.ProcurementLegacyYarnDesignationAdapter;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderLine;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.YarnPurchaseSpecs;
import com.fabricmanagement.procurement.purchaseorder.infra.repository.PurchaseOrderLineRepository;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteLine;
import com.fabricmanagement.procurement.quote.domain.specs.YarnQuoteSpecs;
import com.fabricmanagement.procurement.quote.infra.repository.SupplierQuoteLineRepository;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQLine;
import com.fabricmanagement.procurement.rfq.domain.specs.YarnRFQSpecs;
import com.fabricmanagement.procurement.rfq.infra.repository.SupplierRFQLineRepository;
import com.fabricmanagement.product.yarn.app.port.LegacyDesignationDiscovery;
import com.fabricmanagement.product.yarn.app.port.LegacyDesignationSourceKind;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class YarnLegacyBackfillProcurementAdapterTest {

  @Test
  void resolvesQuoteThroughActiveRfqAndCountsNullOrSoftDeletedTargetsAsUnlinked() {
    UUID tenantId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    Instant recordedAt = Instant.parse("2026-08-31T12:00:00Z");
    PurchaseOrderLine po = mock(PurchaseOrderLine.class);
    when(po.getId()).thenReturn(UUID.randomUUID());
    when(po.getProductId()).thenReturn(productId);
    when(po.getCreatedAt()).thenReturn(recordedAt);
    when(po.getModuleSpecs())
        .thenReturn(
            new YarnPurchaseSpecs(
                "PO Ne 30/2", null, null, null, null, null, null, null, List.of(), null));

    UUID activeRfqId = UUID.randomUUID();
    SupplierRFQLine activeRfq = rfq(activeRfqId, productId, "RFQ Ne 30/2", recordedAt);
    SupplierRFQLine nullProductRfq =
        rfq(UUID.randomUUID(), null, "Unlinked RFQ Ne 20/1", recordedAt);
    SupplierQuoteLine linkedQuote = quote(activeRfqId, "Quote Ne 30/2", recordedAt.minusSeconds(1));
    SupplierQuoteLine nullProductTargetQuote =
        quote(nullProductRfq.getId(), "Unlinked Quote Ne 20/1", recordedAt.minusSeconds(2));
    SupplierQuoteLine softDeletedTargetQuote =
        quote(UUID.randomUUID(), "Unlinked Quote Ne 40/1", recordedAt.minusSeconds(3));

    PurchaseOrderLineRepository purchaseOrders = mock(PurchaseOrderLineRepository.class);
    SupplierRFQLineRepository rfqs = mock(SupplierRFQLineRepository.class);
    SupplierQuoteLineRepository quotes = mock(SupplierQuoteLineRepository.class);
    when(purchaseOrders.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(po));
    when(rfqs.findByTenantIdAndIsActiveTrue(tenantId))
        .thenReturn(List.of(activeRfq, nullProductRfq));
    when(quotes.findByTenantIdAndIsActiveTrue(tenantId))
        .thenReturn(List.of(linkedQuote, nullProductTargetQuote, softDeletedTargetQuote));

    LegacyDesignationDiscovery discovery =
        new ProcurementLegacyYarnDesignationAdapter(purchaseOrders, rfqs, quotes)
            .discover(tenantId);

    assertThat(discovery.records())
        .extracting(record -> record.sourceKind())
        .containsExactly(
            LegacyDesignationSourceKind.PURCHASE_ORDER_AGREED,
            LegacyDesignationSourceKind.RFQ_REQUESTED,
            LegacyDesignationSourceKind.SUPPLIER_QUOTE_OFFERED);
    assertThat(discovery.unlinkedCounts())
        .containsEntry(LegacyDesignationSourceKind.RFQ_REQUESTED, 1L)
        .containsEntry(LegacyDesignationSourceKind.SUPPLIER_QUOTE_OFFERED, 2L);
  }

  private SupplierRFQLine rfq(UUID id, UUID productId, String rawValue, Instant recordedAt) {
    SupplierRFQLine line = mock(SupplierRFQLine.class);
    when(line.getId()).thenReturn(id);
    when(line.getProductId()).thenReturn(productId);
    when(line.getCreatedAt()).thenReturn(recordedAt);
    when(line.getModuleSpecs()).thenReturn(new YarnRFQSpecs(rawValue, null, null, null, null));
    return line;
  }

  private SupplierQuoteLine quote(UUID rfqLineId, String rawValue, Instant recordedAt) {
    SupplierQuoteLine line = mock(SupplierQuoteLine.class);
    when(line.getId()).thenReturn(UUID.randomUUID());
    when(line.getRfqLineId()).thenReturn(rfqLineId);
    when(line.getCreatedAt()).thenReturn(recordedAt);
    when(line.getModuleSpecs()).thenReturn(new YarnQuoteSpecs(rawValue, null, null, null, null));
    return line;
  }
}
