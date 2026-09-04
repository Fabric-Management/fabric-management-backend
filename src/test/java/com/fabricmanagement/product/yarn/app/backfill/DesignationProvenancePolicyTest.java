package com.fabricmanagement.product.yarn.app.backfill;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.product.yarn.app.port.LegacyDesignationRecord;
import com.fabricmanagement.product.yarn.app.port.LegacyDesignationSourceKind;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DesignationProvenancePolicyTest {

  private final DesignationProvenancePolicy policy = new DesignationProvenancePolicy();

  @Test
  void ordersByTierThenRecordedAtThenKindNameThenRecordId() {
    UUID productId = UUID.randomUUID();
    Instant newest = Instant.parse("2026-08-31T12:00:00Z");
    Instant older = newest.minusSeconds(60);
    LegacyDesignationRecord workOrder =
        record(productId, LegacyDesignationSourceKind.WORK_ORDER_TARGET, newest, "ffffffff");
    LegacyDesignationRecord purchaseOrder =
        record(productId, LegacyDesignationSourceKind.PURCHASE_ORDER_AGREED, newest, "ffffffff");
    LegacyDesignationRecord olderBatch =
        record(productId, LegacyDesignationSourceKind.BATCH_ACTUAL, older, "ffffffff");
    LegacyDesignationRecord newerBatch =
        record(productId, LegacyDesignationSourceKind.BATCH_ACTUAL, newest, "00000000");
    LegacyDesignationRecord quote =
        record(productId, LegacyDesignationSourceKind.SUPPLIER_QUOTE_OFFERED, newest, "ffffffff");
    LegacyDesignationRecord rfqLowerId =
        record(productId, LegacyDesignationSourceKind.RFQ_REQUESTED, newest, "00000000");
    LegacyDesignationRecord rfqHigherId =
        record(productId, LegacyDesignationSourceKind.RFQ_REQUESTED, newest, "ffffffff");

    assertThat(
            policy.sorted(
                List.of(
                    workOrder,
                    quote,
                    olderBatch,
                    rfqLowerId,
                    purchaseOrder,
                    newerBatch,
                    rfqHigherId)))
        .containsExactly(
            newerBatch, olderBatch, purchaseOrder, rfqHigherId, rfqLowerId, quote, workOrder);
  }

  private static LegacyDesignationRecord record(
      UUID productId,
      LegacyDesignationSourceKind sourceKind,
      Instant recordedAt,
      String sourceRecordId) {
    return new LegacyDesignationRecord(
        productId, sourceKind, "RING NE 30/2", recordedAt, sourceRecordId);
  }
}
