package com.fabricmanagement.flowboard.generator.domain.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * The Java fingerprint reproduces the SQL {@code md5(concat_ws('|', …))} of the seeded rows. The
 * contents below are copied from V002, V20260602042000 (+ V20260919100300) and V20260706120000; the
 * expected values were measured in the INV-RECIPE-TASK-1 inventory (I5).
 */
class TaskTemplateCatalogueTest {

  @Test
  void fingerprintsOfTheSeededContentMatchThePins() {
    assertThat(
            TaskTemplateCatalogue.fingerprint(
                "Planlama — {salesOrder.orderNumber}",
                "PLANNING",
                null,
                "HIGH",
                "MANAGER",
                new BigDecimal("2.00"),
                null,
                null))
        .isEqualTo(TaskTemplateCatalogue.SALES_ORDER_CONFIRMED__PLANNING.seedFingerprint());
    assertThat(
            TaskTemplateCatalogue.fingerprint(
                "Üretim başlat — {workOrder.orderNumber}",
                "PRODUCTION",
                null,
                "MEDIUM",
                "DEPARTMENT_ADMIN",
                new BigDecimal("8.00"),
                null,
                null))
        .isEqualTo(TaskTemplateCatalogue.WORK_ORDER_APPROVED__PRODUCTION.seedFingerprint());
    assertThat(
            TaskTemplateCatalogue.fingerprint(
                "Depo yerleştirme — {goodsReceipt.receiptNumber}",
                "WAREHOUSE",
                null,
                "MEDIUM",
                "DEPARTMENT_ADMIN",
                new BigDecimal("1.50"),
                null,
                null))
        .isEqualTo(TaskTemplateCatalogue.GOODS_RECEIPT_CONFIRMED__WAREHOUSE.seedFingerprint());
    assertThat(
            TaskTemplateCatalogue.fingerprint(
                "Quote send approval - {quote.quoteNumber}",
                "APPROVAL",
                "GENERAL",
                "HIGH",
                "ANY",
                new BigDecimal("0.25"),
                null,
                null))
        .isEqualTo(TaskTemplateCatalogue.QUOTE_SEND_REQUESTED__APPROVAL.seedFingerprint());
    assertThat(
            TaskTemplateCatalogue.fingerprint(
                "Recipe atanmalı — {certificationReq} / {originReq}",
                "RECIPE_ASSIGNMENT",
                "GENERAL",
                "HIGH",
                "MANAGER",
                new BigDecimal("1.00"),
                "RECIPE_REQUIRED",
                null))
        .isEqualTo(
            TaskTemplateCatalogue.WORK_ORDER_RECIPE_ASSIGNMENT_NEEDED__RECIPE_ASSIGNMENT
                .seedFingerprint());
  }

  @Test
  void keysAreTheEnumNamesAndResolveBack() {
    for (TaskTemplateCatalogue entry : TaskTemplateCatalogue.values()) {
      assertThat(entry.key()).isEqualTo(entry.name());
      assertThat(TaskTemplateCatalogue.byKey(entry.key())).contains(entry);
    }
    assertThat(TaskTemplateCatalogue.byKey("UNKNOWN")).isEmpty();
    assertThat(TaskTemplateCatalogue.byKey(null)).isEmpty();
  }
}
