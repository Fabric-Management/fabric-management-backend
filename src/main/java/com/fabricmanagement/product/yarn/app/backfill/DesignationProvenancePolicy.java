package com.fabricmanagement.product.yarn.app.backfill;

import com.fabricmanagement.product.yarn.app.port.LegacyDesignationRecord;
import com.fabricmanagement.product.yarn.app.port.LegacyDesignationSourceKind;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The sole ranking policy for legacy designation provenance. The total order is tier ascending,
 * recorded-at descending, source-kind name ascending, then source-record id descending.
 */
@Component
public class DesignationProvenancePolicy {

  private static final Comparator<LegacyDesignationRecord> TOTAL_ORDER =
      Comparator.comparingInt((LegacyDesignationRecord record) -> tier(record.sourceKind()))
          .thenComparing(LegacyDesignationRecord::recordedAt, Comparator.reverseOrder())
          .thenComparing(record -> record.sourceKind().name())
          .thenComparing(LegacyDesignationRecord::sourceRecordId, Comparator.reverseOrder());

  public Comparator<LegacyDesignationRecord> comparator() {
    return TOTAL_ORDER;
  }

  public List<LegacyDesignationRecord> sorted(Collection<LegacyDesignationRecord> records) {
    return records.stream().sorted(TOTAL_ORDER).toList();
  }

  public LegacyDesignationRecord preferred(Collection<LegacyDesignationRecord> records) {
    return records.stream().min(TOTAL_ORDER).orElseThrow();
  }

  private static int tier(LegacyDesignationSourceKind kind) {
    return switch (kind) {
      case BATCH_ACTUAL -> 1;
      case PURCHASE_ORDER_AGREED -> 2;
      case SUPPLIER_QUOTE_OFFERED, RFQ_REQUESTED -> 3;
      case WORK_ORDER_TARGET -> 4;
    };
  }
}
