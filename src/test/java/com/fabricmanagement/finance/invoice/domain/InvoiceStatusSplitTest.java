package com.fabricmanagement.finance.invoice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.util.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceStatusSplitTest {

  @Test
  void testInvoicePaymentStatusTransitions() {
    Invoice invoice =
        Invoice.builder()
            .subtotal(Money.zero("USD"))
            .tradingPartnerId(UUID.randomUUID())
            .dueDate(LocalDate.now().plusDays(10))
            .build();

    InvoiceLine line =
        InvoiceLine.builder()
            .quantity(new BigDecimal("1"))
            .unitPrice(new BigDecimal("100.00"))
            .build();
    invoice.addLine(line);
    invoice.recalculateFromLines();

    invoice.issue();
    invoice.send();

    // Initial state
    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
    assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.UNPAID);
    assertThat(invoice.getAmountDue().getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    assertThat(invoice.isOverdue(LocalDate.now())).isFalse();

    // Partial allocation
    invoice.applyAllocation(Money.of(new BigDecimal("40.00"), "USD"), LocalDate.now());
    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT); // Document status remains SENT
    assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PARTIALLY_PAID);
    assertThat(invoice.getAmountDue().getAmount()).isEqualByComparingTo(new BigDecimal("60.00"));

    // Overdue check
    assertThat(invoice.isOverdue(LocalDate.now().plusDays(11)))
        .isTrue(); // Overdue 11 days after issue date

    // Full allocation
    invoice.applyAllocation(Money.of(new BigDecimal("60.00"), "USD"), LocalDate.now());
    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT); // Document status remains SENT
    assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
    assertThat(invoice.getAmountDue().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);

    // No longer overdue after being paid
    assertThat(invoice.isOverdue(LocalDate.now().plusDays(11))).isFalse();
  }
}
