package com.fabricmanagement.finance.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentTest {

  @Test
  void testAllocate_Success() {
    Payment payment =
        Payment.builder()
            .tradingPartnerId(UUID.randomUUID())
            .paymentNumber("PAY-1")
            .direction(PaymentDirection.INBOUND)
            .amount(Money.of(new java.math.BigDecimal("100.00"), "USD"))
            .paymentDate(LocalDate.now())
            .build();

    UUID invoiceId = UUID.randomUUID();
    Money allocAmount = Money.of(new java.math.BigDecimal("40.00"), "USD");
    Money openBalance = Money.of(new java.math.BigDecimal("50.00"), "USD");

    PaymentAllocation allocation = payment.allocate(invoiceId, allocAmount, openBalance, "USD");

    assertThat(allocation.getAmount()).isEqualTo(allocAmount);
    assertThat(payment.getAllocatedAmount())
        .isEqualTo(Money.of(new java.math.BigDecimal("40.00"), "USD"));
    assertThat(payment.getUnallocatedAmount())
        .isEqualTo(Money.of(new java.math.BigDecimal("60.00"), "USD"));
  }

  @Test
  void testAllocate_FailsIfCrossCurrency() {
    Payment payment =
        Payment.builder()
            .tradingPartnerId(UUID.randomUUID())
            .paymentNumber("PAY-1")
            .direction(PaymentDirection.INBOUND)
            .amount(Money.of(new java.math.BigDecimal("100.00"), "USD"))
            .paymentDate(LocalDate.now())
            .build();

    assertThatThrownBy(
            () ->
                payment.allocate(
                    UUID.randomUUID(),
                    Money.of(new java.math.BigDecimal("40.00"), "EUR"),
                    Money.of(new java.math.BigDecimal("50.00"), "EUR"),
                    "EUR"))
        .isInstanceOf(FinanceDomainException.class)
        .hasMessageContaining("Cross-currency allocation not supported");
  }

  @Test
  void testAllocate_FailsIfAmountExceedsUnallocated() {
    Payment payment =
        Payment.builder()
            .tradingPartnerId(UUID.randomUUID())
            .paymentNumber("PAY-1")
            .direction(PaymentDirection.INBOUND)
            .amount(Money.of(new java.math.BigDecimal("100.00"), "USD"))
            .paymentDate(LocalDate.now())
            .build();

    assertThatThrownBy(
            () ->
                payment.allocate(
                    UUID.randomUUID(),
                    Money.of(new java.math.BigDecimal("110.00"), "USD"),
                    Money.of(new java.math.BigDecimal("200.00"), "USD"),
                    "USD"))
        .isInstanceOf(FinanceDomainException.class)
        .hasMessageContaining("exceeds unallocated payment amount");
  }

  @Test
  void testAllocate_FailsIfAmountExceedsInvoiceOpenBalance() {
    Payment payment =
        Payment.builder()
            .tradingPartnerId(UUID.randomUUID())
            .paymentNumber("PAY-1")
            .direction(PaymentDirection.INBOUND)
            .amount(Money.of(new java.math.BigDecimal("100.00"), "USD"))
            .paymentDate(LocalDate.now())
            .build();

    assertThatThrownBy(
            () ->
                payment.allocate(
                    UUID.randomUUID(),
                    Money.of(new java.math.BigDecimal("60.00"), "USD"),
                    Money.of(new java.math.BigDecimal("50.00"), "USD"),
                    "USD"))
        .isInstanceOf(FinanceDomainException.class)
        .hasMessageContaining("exceeds invoice open balance");
  }

  @Test
  void testDeallocate_Success() {
    Payment payment =
        Payment.builder()
            .tradingPartnerId(UUID.randomUUID())
            .paymentNumber("PAY-1")
            .direction(PaymentDirection.INBOUND)
            .amount(Money.of(new java.math.BigDecimal("100.00"), "USD"))
            .paymentDate(LocalDate.now())
            .build();

    UUID invoiceId = UUID.randomUUID();
    PaymentAllocation allocation =
        payment.allocate(
            invoiceId,
            Money.of(new java.math.BigDecimal("40.00"), "USD"),
            Money.of(new java.math.BigDecimal("50.00"), "USD"),
            "USD");

    // Mock ID for allocation
    allocation.setId(UUID.randomUUID());

    payment.deallocate(allocation.getId());

    assertThat(allocation.getIsActive()).isFalse();
    assertThat(payment.getAllocatedAmount()).isEqualTo(Money.zero("USD"));
  }
}
