package com.fabricmanagement.procurement.purchaseorder.app.adapter;

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
import com.fabricmanagement.product.yarn.app.port.LegacyDesignationRecord;
import com.fabricmanagement.product.yarn.app.port.LegacyDesignationSourceKind;
import com.fabricmanagement.product.yarn.app.port.LegacyYarnDesignationSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Bulk procurement-side discovery across PO, RFQ and quote lines without status filtering. */
@Component
@RequiredArgsConstructor
public class ProcurementLegacyYarnDesignationAdapter implements LegacyYarnDesignationSource {

  private final PurchaseOrderLineRepository purchaseOrderLineRepository;
  private final SupplierRFQLineRepository rfqLineRepository;
  private final SupplierQuoteLineRepository quoteLineRepository;

  @Override
  public LegacyDesignationDiscovery discover(UUID tenantId) {
    List<LegacyDesignationRecord> records = new ArrayList<>();
    EnumMap<LegacyDesignationSourceKind, Long> unlinked =
        new EnumMap<>(LegacyDesignationSourceKind.class);

    for (PurchaseOrderLine line :
        purchaseOrderLineRepository.findByTenantIdAndIsActiveTrue(tenantId)) {
      if (line.getModuleSpecs() instanceof YarnPurchaseSpecs specs) {
        addOrCountUnlinked(
            records,
            unlinked,
            line.getProductId(),
            LegacyDesignationSourceKind.PURCHASE_ORDER_AGREED,
            specs.yarnCount(),
            line.getCreatedAt(),
            line.getId());
      }
    }

    List<SupplierRFQLine> activeRfqLines =
        rfqLineRepository.findByTenantIdAndIsActiveTrue(tenantId);
    Map<UUID, SupplierRFQLine> activeRfqById = new HashMap<>();
    for (SupplierRFQLine line : activeRfqLines) {
      activeRfqById.put(line.getId(), line);
      if (line.getModuleSpecs() instanceof YarnRFQSpecs specs) {
        addOrCountUnlinked(
            records,
            unlinked,
            line.getProductId(),
            LegacyDesignationSourceKind.RFQ_REQUESTED,
            specs.requiredYarnCount(),
            line.getCreatedAt(),
            line.getId());
      }
    }

    for (SupplierQuoteLine line : quoteLineRepository.findByTenantIdAndIsActiveTrue(tenantId)) {
      if (!(line.getModuleSpecs() instanceof YarnQuoteSpecs specs)) {
        continue;
      }
      SupplierRFQLine rfqLine = activeRfqById.get(line.getRfqLineId());
      UUID productId = rfqLine == null ? null : rfqLine.getProductId();
      addOrCountUnlinked(
          records,
          unlinked,
          productId,
          LegacyDesignationSourceKind.SUPPLIER_QUOTE_OFFERED,
          specs.yarnCount(),
          line.getCreatedAt(),
          line.getId());
    }

    return new LegacyDesignationDiscovery(records, unlinked);
  }

  private void addOrCountUnlinked(
      List<LegacyDesignationRecord> records,
      Map<LegacyDesignationSourceKind, Long> unlinked,
      UUID productId,
      LegacyDesignationSourceKind kind,
      String rawValue,
      Instant recordedAt,
      UUID sourceRecordId) {
    if (productId == null) {
      if (rawValue != null && !rawValue.isBlank()) {
        unlinked.merge(kind, 1L, Long::sum);
      }
      return;
    }
    records.add(
        new LegacyDesignationRecord(
            productId, kind, rawValue, recordedAt, sourceRecordId.toString()));
  }
}
