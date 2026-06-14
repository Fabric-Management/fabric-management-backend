package com.fabricmanagement.finance.invoice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InvoiceTaxLineTest {

  @Test
  @DisplayName("1. Multi-rate invoice generates multiple tax lines and clears header rate")
  void testMultiRateInvoice() {
    Invoice invoice =
        Invoice.builder().subtotal(com.fabricmanagement.common.util.Money.zero("TRY")).build();

    // Line 1: 20% STANDARD, 100 * 2 = 200, tax = 40
    InvoiceLine line1 =
        InvoiceLine.builder()
            .quantity(new BigDecimal("2"))
            .unitPrice(new BigDecimal("100"))
            .taxRate(new BigDecimal("20"))
            .taxCategory(TaxCategory.STANDARD)
            .build();

    // Line 2: 5% REDUCED, 50 * 4 = 200, tax = 10
    InvoiceLine line2 =
        InvoiceLine.builder()
            .quantity(new BigDecimal("4"))
            .unitPrice(new BigDecimal("50"))
            .taxRate(new BigDecimal("5"))
            .taxCategory(TaxCategory.REDUCED)
            .build();

    // Line 3: 0% ZERO_RATED, 1000 * 1 = 1000, tax = 0
    InvoiceLine line3 =
        InvoiceLine.builder()
            .quantity(new BigDecimal("1"))
            .unitPrice(new BigDecimal("1000"))
            .taxRate(new BigDecimal("0"))
            .taxCategory(TaxCategory.ZERO_RATED)
            .build();

    invoice.addLine(line1);
    invoice.addLine(line2);
    invoice.addLine(line3);

    invoice.recalculateFromLines();

    List<InvoiceTaxLine> taxLines = invoice.getTaxLines();
    assertThat(taxLines).hasSize(3);

    assertThat(taxLines)
        .anySatisfy(
            tl -> {
              assertThat(tl.getTaxCategory()).isEqualTo(TaxCategory.STANDARD);
              assertThat(tl.getTaxRate()).isEqualByComparingTo("20");
              assertThat(tl.getTaxableBase()).isEqualByComparingTo("200");
              assertThat(tl.getTaxAmount()).isEqualByComparingTo("40");
            })
        .anySatisfy(
            tl -> {
              assertThat(tl.getTaxCategory()).isEqualTo(TaxCategory.REDUCED);
              assertThat(tl.getTaxRate()).isEqualByComparingTo("5");
              assertThat(tl.getTaxableBase()).isEqualByComparingTo("200");
              assertThat(tl.getTaxAmount()).isEqualByComparingTo("10");
            })
        .anySatisfy(
            tl -> {
              assertThat(tl.getTaxCategory()).isEqualTo(TaxCategory.ZERO_RATED);
              assertThat(tl.getTaxRate()).isEqualByComparingTo("0");
              assertThat(tl.getTaxableBase()).isEqualByComparingTo("1000");
              assertThat(tl.getTaxAmount()).isEqualByComparingTo("0");
            });

    assertThat(invoice.getTaxAmount().getAmount()).isEqualByComparingTo("50");
    assertThat(invoice.getTaxRate()).isNull(); // Multi-rate
  }

  @Test
  @DisplayName("2. Single-rate invoice generates one tax line and keeps header rate")
  void testSingleRateInvoice() {
    Invoice invoice =
        Invoice.builder().subtotal(com.fabricmanagement.common.util.Money.zero("TRY")).build();

    InvoiceLine line1 =
        InvoiceLine.builder()
            .quantity(new BigDecimal("1"))
            .unitPrice(new BigDecimal("100"))
            .taxRate(new BigDecimal("18"))
            .taxCategory(TaxCategory.STANDARD)
            .build();

    InvoiceLine line2 =
        InvoiceLine.builder()
            .quantity(new BigDecimal("2"))
            .unitPrice(new BigDecimal("50"))
            .taxRate(new BigDecimal("18"))
            .taxCategory(TaxCategory.STANDARD)
            .build();

    invoice.addLine(line1);
    invoice.addLine(line2);

    invoice.recalculateFromLines();

    List<InvoiceTaxLine> taxLines = invoice.getTaxLines();
    assertThat(taxLines).hasSize(1);

    InvoiceTaxLine tl = taxLines.get(0);
    assertThat(tl.getTaxCategory()).isEqualTo(TaxCategory.STANDARD);
    assertThat(tl.getTaxRate()).isEqualByComparingTo("18");
    assertThat(tl.getTaxableBase()).isEqualByComparingTo("200");
    assertThat(tl.getTaxAmount()).isEqualByComparingTo("36");

    assertThat(invoice.getTaxAmount().getAmount()).isEqualByComparingTo("36");
    assertThat(invoice.getTaxRate()).isEqualByComparingTo("18");
  }

  @Test
  @DisplayName("3. REVERSE_CHARGE line zeros out lineTax but keeps base and rate info")
  void testReverseChargeLine() {
    Invoice invoice =
        Invoice.builder().subtotal(com.fabricmanagement.common.util.Money.zero("TRY")).build();

    InvoiceLine line1 =
        InvoiceLine.builder()
            .quantity(new BigDecimal("1"))
            .unitPrice(new BigDecimal("1000"))
            .taxRate(new BigDecimal("20")) // Standard rate nominally
            .taxCategory(TaxCategory.REVERSE_CHARGE)
            .build();

    invoice.addLine(line1);
    invoice.recalculateFromLines();

    // R2 fix check: lineTotal should equal afterDiscount (net, no tax)
    assertThat(line1.getLineTotal()).isEqualByComparingTo("1000");
    assertThat(line1.getLineTax()).isEqualByComparingTo("0");

    List<InvoiceTaxLine> taxLines = invoice.getTaxLines();
    assertThat(taxLines).hasSize(1);

    InvoiceTaxLine tl = taxLines.get(0);
    assertThat(tl.getTaxCategory()).isEqualTo(TaxCategory.REVERSE_CHARGE);
    assertThat(tl.getTaxRate()).isEqualByComparingTo("20");
    assertThat(tl.getTaxableBase()).isEqualByComparingTo("1000");
    assertThat(tl.getTaxAmount()).isEqualByComparingTo("0"); // Zeros out

    assertThat(invoice.getTaxAmount().getAmount()).isEqualByComparingTo("0");
    assertThat(invoice.getTaxRate()).isEqualByComparingTo("20");
  }

  @Test
  @DisplayName("4. Line without explicit taxCategory defaults to STANDARD")
  void testDefaultTaxCategory() {
    InvoiceLine line =
        InvoiceLine.builder()
            .quantity(new BigDecimal("1"))
            .unitPrice(new BigDecimal("100"))
            .build();

    assertThat(line.getTaxCategory()).isEqualTo(TaxCategory.STANDARD);
  }
}
