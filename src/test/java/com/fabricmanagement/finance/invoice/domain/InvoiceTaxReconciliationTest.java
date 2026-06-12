package com.fabricmanagement.finance.invoice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceTaxReconciliationTest {

  @Test
  void recalculate_twoLines_derivesCorrectHeaderAmounts() {
    Invoice invoice =
        Invoice.builder().subtotal(Money.zero("GBP")).tradingPartnerId(UUID.randomUUID()).build();

    // qty=100 × price=50.00, taxRate=18%, discountRate=10%
    // lineSubtotal: 5000.0000
    // lineDiscount: 5000.0000 * 10% = 500.0000
    // afterDiscount: 4500.0000
    // lineTax: 4500.0000 * 18% = 810.0000
    // lineTotal: 4500.0000 + 810.0000 = 5310.0000
    InvoiceLine line1 =
        InvoiceLine.builder()
            .quantity(new BigDecimal("100"))
            .unitPrice(new BigDecimal("50.00"))
            .taxRate(new BigDecimal("18"))
            .discountRate(new BigDecimal("10"))
            .build();
    invoice.addLine(line1);

    // qty=200 × price=30.00, taxRate=18%, discountRate=0%
    // lineSubtotal: 6000.0000
    // lineDiscount: 0.0000
    // afterDiscount: 6000.0000
    // lineTax: 6000.0000 * 18% = 1080.0000
    // lineTotal: 6000.0000 + 1080.0000 = 7080.0000
    InvoiceLine line2 =
        InvoiceLine.builder()
            .quantity(new BigDecimal("200"))
            .unitPrice(new BigDecimal("30.00"))
            .taxRate(new BigDecimal("18"))
            .discountRate(BigDecimal.ZERO)
            .build();
    invoice.addLine(line2);

    invoice.recalculateFromLines();

    // Expected sum:
    // subtotal: 11000.00
    // discount: 500.00
    // tax: 1890.00
    // total: 11000.00 - 500.00 + 1890.00 = 12390.00
    assertThat(invoice.getSubtotal().getAmount()).isEqualByComparingTo(new BigDecimal("11000.00"));
    assertThat(invoice.getDiscountAmount().getAmount())
        .isEqualByComparingTo(new BigDecimal("500.00"));
    assertThat(invoice.getTaxAmount().getAmount()).isEqualByComparingTo(new BigDecimal("1890.00"));

    // totalAmount should strictly equal subtotal - discountAmount + taxAmount
    BigDecimal expectedTotal =
        new BigDecimal("11000.00")
            .subtract(new BigDecimal("500.00"))
            .add(new BigDecimal("1890.00"));
    assertThat(invoice.getTotalAmount().getAmount()).isEqualByComparingTo(expectedTotal);

    // Verify it's within 0.01 tolerance of sum of line totals (12390.00)
    BigDecimal sumLineTotals = line1.getLineTotal().add(line2.getLineTotal());
    assertThat(invoice.getTotalAmount().getAmount().subtract(sumLineTotals).abs())
        .isLessThanOrEqualTo(new BigDecimal("0.01"));
  }

  @Test
  void recalculate_headerTaxAlsoSupplied_derivedValuesWin() {
    Invoice invoice =
        Invoice.builder().subtotal(Money.zero("GBP")).tradingPartnerId(UUID.randomUUID()).build();
    InvoiceLine line1 =
        InvoiceLine.builder()
            .quantity(new BigDecimal("100"))
            .unitPrice(new BigDecimal("50.00"))
            .taxRate(new BigDecimal("18"))
            .discountRate(new BigDecimal("10"))
            .build();
    invoice.addLine(line1);

    // Client maliciously/erroneously sets header tax
    invoice.setTaxAmount(Money.of(new BigDecimal("9999.00"), "GBP"));

    invoice.recalculateFromLines();

    // Derived values win
    assertThat(invoice.getTaxAmount().getAmount()).isEqualByComparingTo(new BigDecimal("810.00"));
    assertThat(invoice.getTotalAmount().getAmount())
        .isEqualByComparingTo(new BigDecimal("5310.00"));
  }

  @Test
  void recalculate_emptyLines_noOpPreservesHeaderValues() {
    Invoice invoice =
        Invoice.builder()
            .tradingPartnerId(UUID.randomUUID())
            .subtotal(Money.of(new BigDecimal("1000.00"), "GBP"))
            .taxAmount(Money.of(new BigDecimal("180.00"), "GBP"))
            .build();

    invoice.recalculateFromLines();

    // Subtotal should remain unchanged — recalculateFromLines() is a no-op on empty lines
    assertThat(invoice.getSubtotal().getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
    assertThat(invoice.getTaxAmount().getAmount()).isEqualByComparingTo(new BigDecimal("180.00"));
  }

  @Test
  void calculateAmounts_headerOnlyInvoice_usesClientSuppliedValues() {
    Invoice invoice =
        Invoice.builder()
            .tradingPartnerId(UUID.randomUUID())
            .subtotal(Money.of(new BigDecimal("1000.00"), "GBP"))
            .discountAmount(Money.of(new BigDecimal("100.00"), "GBP"))
            .taxAmount(Money.of(new BigDecimal("162.00"), "GBP"))
            .build();

    invoice.calculateAmounts();

    assertThat(invoice.getTotalAmount().getAmount())
        .isEqualByComparingTo(new BigDecimal("1062.00"));
  }

  @Test
  void validateClientAmounts_totalMismatch_throwsExceptionWithDiagnosticValues() {
    Invoice invoice =
        Invoice.builder().subtotal(Money.zero("GBP")).tradingPartnerId(UUID.randomUUID()).build();
    InvoiceLine line1 =
        InvoiceLine.builder()
            .quantity(new BigDecimal("100"))
            .unitPrice(new BigDecimal("50.00"))
            .build(); // subtotal: 5000
    invoice.addLine(line1);
    invoice.recalculateFromLines(); // totalAmount becomes 5000.00

    assertThatThrownBy(() -> invoice.validateClientAmounts(null, new BigDecimal("5001.00")))
        .isInstanceOf(FinanceDomainException.class)
        .hasMessageContaining("does not match line-derived total")
        .hasMessageContaining("5001.00")
        .hasMessageContaining("5000.00");
  }

  @Test
  void validateClientAmounts_subtotalMismatch_throwsExceptionWithDiagnosticValues() {
    Invoice invoice =
        Invoice.builder().subtotal(Money.zero("GBP")).tradingPartnerId(UUID.randomUUID()).build();
    InvoiceLine line1 =
        InvoiceLine.builder()
            .quantity(new BigDecimal("100"))
            .unitPrice(new BigDecimal("50.00"))
            .build(); // subtotal: 5000
    invoice.addLine(line1);
    invoice.recalculateFromLines(); // subtotal becomes 5000.00

    assertThatThrownBy(() -> invoice.validateClientAmounts(new BigDecimal("4999.00"), null))
        .isInstanceOf(FinanceDomainException.class)
        .hasMessageContaining("does not match line-derived subtotal")
        .hasMessageContaining("4999.00")
        .hasMessageContaining("5000.00");
  }

  @Test
  void validateClientAmounts_withinTolerance_noException() {
    Invoice invoice =
        Invoice.builder().subtotal(Money.zero("GBP")).tradingPartnerId(UUID.randomUUID()).build();
    InvoiceLine line1 =
        InvoiceLine.builder()
            .quantity(new BigDecimal("100"))
            .unitPrice(new BigDecimal("50.00"))
            .build(); // subtotal: 5000
    invoice.addLine(line1);
    invoice.recalculateFromLines(); // totalAmount becomes 5000.00

    // tolerance is 0.01, so +0.005 is fine
    invoice.validateClientAmounts(new BigDecimal("5000.005"), new BigDecimal("5000.005"));
    // should not throw exception
  }

  @Test
  void recordPayment_afterRecalculation_correctTransition() {
    Invoice invoice =
        Invoice.builder().subtotal(Money.zero("GBP")).tradingPartnerId(UUID.randomUUID()).build();
    InvoiceLine line1 =
        InvoiceLine.builder()
            .quantity(new BigDecimal("100"))
            .unitPrice(new BigDecimal("50.00"))
            .build(); // 5000
    invoice.addLine(line1);
    invoice.recalculateFromLines();

    invoice.issue();
    invoice.send();

    invoice.recordPayment(Money.of(new BigDecimal("2000.00"), "GBP"));
    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
    assertThat(invoice.getAmountDue().getAmount()).isEqualByComparingTo(new BigDecimal("3000.00"));

    invoice.recordPayment(Money.of(new BigDecimal("3000.00"), "GBP"));
    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
    assertThat(invoice.getAmountDue().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
  }
}
