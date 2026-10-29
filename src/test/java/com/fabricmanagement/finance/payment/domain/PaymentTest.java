package com.fabricmanagement.finance.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import com.fabricmanagement.finance.payment.domain.exception.PaymentReversalException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

  private static Payment persisted(PaymentDirection direction, PaymentMethod method) {
    Payment payment =
        Payment.builder()
            .tradingPartnerId(UUID.randomUUID())
            .paymentNumber("PAY-1")
            .direction(direction)
            .method(method)
            .amount(Money.of(new BigDecimal("100.00"), "USD"))
            .paymentDate(LocalDate.of(2026, 3, 10))
            .build();
    payment.setId(UUID.randomUUID());
    return payment;
  }

  private static void assertRefused(ThrowingCallable call, String code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            PaymentReversalException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
  }

  @ParameterizedTest
  @EnumSource(PaymentMethod.class)
  void reverse_worksForEveryPaymentMethodAndKeepsTheMethod(PaymentMethod method) {
    Payment original = persisted(PaymentDirection.INBOUND, method);

    Payment reversal =
        original.reverse("PAY-2", LocalDate.of(2026, 5, 2), "Entered twice").payment();

    assertThat(reversal.getMethod()).isEqualTo(method);
    assertThat(reversal.getDirection()).isEqualTo(PaymentDirection.OUTBOUND);
    assertThat(reversal.getAmount()).isEqualTo(original.getAmount());
    assertThat(reversal.getTradingPartnerId()).isEqualTo(original.getTradingPartnerId());
    assertThat(reversal.getPaymentNumber()).isEqualTo("PAY-2");
    assertThat(reversal.getPaymentDate()).isEqualTo(LocalDate.of(2026, 5, 2));
    assertThat(reversal.getStatus()).isEqualTo(PaymentStatus.RECEIVED);
    assertThat(reversal.getReversesPaymentId()).isEqualTo(original.getId());
    assertThat(reversal.getReversalReason()).isEqualTo("Entered twice");
    assertThat(reversal.isReversal()).isTrue();
  }

  @Test
  void reverse_flipsAnOutboundPaymentToInbound() {
    Payment original = persisted(PaymentDirection.OUTBOUND, PaymentMethod.BANK_TRANSFER);

    Payment reversal =
        original.reverse("PAY-2", LocalDate.of(2026, 3, 10), "Wrong supplier").payment();

    assertThat(reversal.getDirection()).isEqualTo(PaymentDirection.INBOUND);
  }

  @Test
  void reverse_leavesTheOriginalAsRecordedUntilItIsLinked() {
    Payment original = persisted(PaymentDirection.INBOUND, PaymentMethod.CASH);

    Payment reversal =
        original.reverse("PAY-2", LocalDate.of(2026, 3, 10), "Wrong customer").payment();

    assertThat(original.getStatus()).isEqualTo(PaymentStatus.RECEIVED);
    assertThat(original.isReversed()).isFalse();
    assertThat(original.getAmount()).isEqualTo(Money.of(new BigDecimal("100.00"), "USD"));

    reversal.setId(UUID.randomUUID());
    original.markReversedBy(reversal.getId());

    assertThat(original.isReversed()).isTrue();
    assertThat(original.getReversedByPaymentId()).isEqualTo(reversal.getId());
    assertThat(original.getStatus()).isEqualTo(PaymentStatus.RECEIVED);
  }

  @Test
  void reverse_refusesAPaymentThatIsAlreadyReversed() {
    Payment original = persisted(PaymentDirection.INBOUND, PaymentMethod.CASH);
    original.markReversedBy(UUID.randomUUID());

    assertRefused(
        () -> original.reverse("PAY-3", LocalDate.of(2026, 4, 1), "Again"),
        "PAYMENT_ALREADY_REVERSED");
    assertRefused(() -> original.markReversedBy(UUID.randomUUID()), "PAYMENT_ALREADY_REVERSED");
  }

  @Test
  void reverse_refusesToReverseAReversal() {
    Payment original = persisted(PaymentDirection.INBOUND, PaymentMethod.CASH);
    Payment reversal =
        original.reverse("PAY-2", LocalDate.of(2026, 3, 10), "Wrong customer").payment();
    reversal.setId(UUID.randomUUID());

    assertRefused(
        () -> reversal.reverse("PAY-3", LocalDate.of(2026, 3, 11), "Undo"),
        "PAYMENT_REVERSAL_NOT_REVERSIBLE");
  }

  @Test
  void reverse_refusesAVoidedPayment() {
    Payment original = persisted(PaymentDirection.INBOUND, PaymentMethod.CASH);
    original.voidPayment();

    assertRefused(
        () -> original.reverse("PAY-2", LocalDate.of(2026, 4, 1), "Late"), "PAYMENT_VOIDED");
  }

  private static PaymentAllocation allocated(Payment payment, String amount) {
    PaymentAllocation allocation =
        payment.allocate(
            UUID.randomUUID(),
            Money.of(new BigDecimal(amount), "USD"),
            Money.of(new BigDecimal("100.00"), "USD"),
            "USD");
    allocation.setId(UUID.randomUUID());
    return allocation;
  }

  @Test
  void reverse_releasesTheActiveAllocationsAndReportsThemForTheInvoices() {
    Payment original = persisted(PaymentDirection.INBOUND, PaymentMethod.BANK_TRANSFER);
    PaymentAllocation first = allocated(original, "40.00");
    PaymentAllocation second = allocated(original, "25.00");

    Payment.Reversal reversal = original.reverse("PAY-2", LocalDate.of(2026, 4, 1), "Mistake");

    assertThat(reversal.releasedAllocations()).containsExactly(first, second);
    assertThat(first.getIsActive()).isFalse();
    assertThat(second.getIsActive()).isFalse();
    assertThat(original.getAllocatedAmount()).isEqualTo(Money.zero("USD"));
    // The reversal nets the payment; it is never applied to invoices itself.
    assertThat(reversal.payment().getAllocations()).isEmpty();
  }

  @Test
  void reverse_leavesAllocationsAloneWhenItIsRefused() {
    Payment original = persisted(PaymentDirection.INBOUND, PaymentMethod.CASH);
    PaymentAllocation allocation = allocated(original, "40.00");

    assertThatThrownBy(() -> original.reverse("PAY-2", LocalDate.of(2026, 3, 9), "Too early"))
        .isInstanceOf(PaymentReversalException.class);

    assertThat(allocation.getIsActive()).isTrue();
    assertThat(original.getAllocatedAmount()).isEqualTo(Money.of(new BigDecimal("40.00"), "USD"));
  }

  @Test
  void voidPayment_isRefusedWhileInvoiceAllocationsAreActive() {
    Payment original = persisted(PaymentDirection.INBOUND, PaymentMethod.CASH);
    PaymentAllocation allocation = allocated(original, "40.00");

    assertRefused(original::voidPayment, "PAYMENT_VOID_HAS_ALLOCATIONS");

    assertThat(original.getStatus()).isEqualTo(PaymentStatus.RECEIVED);
    assertThat(allocation.getIsActive()).isTrue();
  }

  @Test
  void voidPayment_stillVoidsAPaymentNothingWasAppliedTo() {
    Payment original = persisted(PaymentDirection.INBOUND, PaymentMethod.CASH);
    PaymentAllocation allocation = allocated(original, "40.00");
    original.deallocate(allocation.getId());

    original.voidPayment();

    assertThat(original.getStatus()).isEqualTo(PaymentStatus.VOIDED);
  }

  @Test
  void reverse_rejectsADateBeforeThePaymentDateAndAMissingReason() {
    Payment original = persisted(PaymentDirection.INBOUND, PaymentMethod.CASH);

    assertThatThrownBy(() -> original.reverse("PAY-2", LocalDate.of(2026, 3, 9), "Early"))
        .isInstanceOf(PaymentReversalException.class)
        .hasMessageContaining("cannot precede");
    assertThatThrownBy(() -> original.reverse("PAY-2", LocalDate.of(2026, 3, 10), "  "))
        .isInstanceOf(PaymentReversalException.class)
        .hasMessageContaining("needs a reason");
    assertThatThrownBy(() -> original.reverse("PAY-2", null, "No date"))
        .isInstanceOf(PaymentReversalException.class);
  }

  @Test
  void reverse_refusesAPaymentThatWasNeverSaved() {
    Payment unsaved =
        Payment.builder()
            .tradingPartnerId(UUID.randomUUID())
            .paymentNumber("PAY-1")
            .direction(PaymentDirection.INBOUND)
            .amount(Money.of(new BigDecimal("100.00"), "USD"))
            .paymentDate(LocalDate.of(2026, 3, 10))
            .build();

    assertThatThrownBy(() -> unsaved.reverse("PAY-2", LocalDate.of(2026, 3, 10), "Oops"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aReversedPaymentAndAReversalCanNeitherBeAllocatedNorVoided() {
    Payment original = persisted(PaymentDirection.INBOUND, PaymentMethod.CASH);
    Payment reversal =
        original.reverse("PAY-2", LocalDate.of(2026, 3, 10), "Wrong customer").payment();
    reversal.setId(UUID.randomUUID());
    original.markReversedBy(reversal.getId());
    Money part = Money.of(new BigDecimal("10.00"), "USD");

    for (Payment payment : new Payment[] {original, reversal}) {
      assertRefused(
          () -> payment.allocate(UUID.randomUUID(), part, part, "USD"), "PAYMENT_REVERSED");
    }
    assertRefused(original::voidPayment, "PAYMENT_ALREADY_REVERSED");
    assertRefused(reversal::voidPayment, "PAYMENT_REVERSAL_IMMUTABLE");
    assertThat(original.getStatus()).isEqualTo(PaymentStatus.RECEIVED);
    assertThat(reversal.getStatus()).isEqualTo(PaymentStatus.RECEIVED);
  }
}
