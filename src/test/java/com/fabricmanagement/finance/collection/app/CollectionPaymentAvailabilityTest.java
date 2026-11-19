package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.collection.domain.CollectionCheque;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.ChequeAction;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.ChequeState;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind;
import com.fabricmanagement.finance.collection.infra.repository.CollectionChequeRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository;
import com.fabricmanagement.finance.payment.domain.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

/** PAYMENT-PLAN-9 R5: one formula for the match guard (single) and the customer view (batch). */
@ExtendWith(MockitoExtension.class)
class CollectionPaymentAvailabilityTest {
  @Mock CollectionPaymentMatchRepository matches;
  @Mock CollectionRefundLedger refunds;
  @Mock CollectionChequeRepository cheques;
  @InjectMocks CollectionPaymentAvailability availability;
  final UUID tenant = UUID.randomUUID(), partner = UUID.randomUUID();
  final LocalDate date = LocalDate.of(2026, 10, 20);

  private Payment payment(PaymentMethod method, String amount) {
    var value =
        Payment.builder()
            .tradingPartnerId(partner)
            .paymentNumber("PAY-GB-" + UUID.randomUUID().toString().substring(0, 4))
            .direction(PaymentDirection.INBOUND)
            .method(method)
            .amount(Money.of(new BigDecimal(amount), "GBP"))
            .paymentDate(date)
            .build();
    value.setId(UUID.randomUUID());
    value.setTenantId(tenant);
    return value;
  }

  private CollectionPaymentMatchRepository.Balance balance(UUID id, String amount) {
    var value = mock(CollectionPaymentMatchRepository.Balance.class);
    lenient().when(value.getId()).thenReturn(id);
    lenient().when(value.getAmount()).thenReturn(new BigDecimal(amount));
    return value;
  }

  @Test
  void singleAndBatchGiveTheSameAmountAfterMatchesAndRefundHolds() {
    var transfer = payment(PaymentMethod.BANK_TRANSFER, "10000.00");
    UUID id = transfer.getId();
    when(matches.matchedPayment(tenant, id)).thenReturn(new BigDecimal("7000.00"));
    when(refunds.held(tenant, id)).thenReturn(new BigDecimal("500.00"));
    // Built before stubbing: a mock stubbed inside thenReturn(...) leaves the outer stub
    // unfinished.
    var matched = balance(id, "7000.00");
    when(matches.matchedPayments(tenant, List.of(id))).thenReturn(List.of(matched));
    when(refunds.heldByPayment(tenant, List.of(id)))
        .thenReturn(Map.of(id, new BigDecimal("500.00")));
    var single = availability.of(tenant, transfer);
    var batch = availability.of(tenant, List.of(transfer)).get(id);
    assertThat(single.available()).isEqualByComparingTo("2500.00");
    assertThat(batch).isEqualTo(single);
    assertThat(single.eligible()).isTrue();
  }

  @Test
  void neverBelowZeroAndInvoiceAllocationsAreNotSubtracted() {
    var transfer = payment(PaymentMethod.BANK_TRANSFER, "1000.00");
    when(matches.matchedPayment(tenant, transfer.getId())).thenReturn(new BigDecimal("900.00"));
    when(refunds.held(tenant, transfer.getId())).thenReturn(new BigDecimal("200.00"));
    assertThat(availability.of(tenant, transfer).available()).isEqualByComparingTo("0");
    verify(matches, never()).invoiceAllocated(any(), any());
  }

  @Test
  void voidedReversedReversalsAndUnverifiedChequePaymentsAreNotEligible() {
    var voided = payment(PaymentMethod.BANK_TRANSFER, "100.00");
    voided.voidPayment();
    var reversed = payment(PaymentMethod.BANK_TRANSFER, "100.00");
    reversed.setReversedByPaymentId(UUID.randomUUID());
    var reversal = payment(PaymentMethod.BANK_TRANSFER, "100.00");
    reversal.setReversesPaymentId(UUID.randomUUID());
    var legacyCheque = payment(PaymentMethod.CHEQUE, "100.00");
    var clearing = payment(PaymentMethod.CHEQUE, "100.00");
    var cheque =
        CollectionCheque.receive(
            partner,
            "GBP",
            new BigDecimal("100.00"),
            date,
            date.plusMonths(1),
            "NORTHBANK-0042",
            "Signed instrument scan",
            new CollectionCheque.Drawer(DrawerKind.OWN, null, "Northbridge Cloth"));
    cheque.setId(UUID.randomUUID());
    cheque.transition(ChequeAction.CLEAR, clearing.getId(), "Bank confirmed clearing");
    var all = List.of(voided, reversed, reversal, legacyCheque, clearing);
    when(cheques.findByTenantIdAndClearedPaymentIdInAndState(
            eq(tenant), anyCollection(), eq(ChequeState.CLEARED)))
        .thenReturn(List.of(cheque));
    var result = availability.of(tenant, all);
    assertThat(result.get(voided.getId()).eligible()).isFalse();
    assertThat(result.get(reversed.getId()).eligible()).isFalse();
    assertThat(result.get(reversal.getId()).eligible()).isFalse();
    assertThat(result.get(legacyCheque.getId()).eligible()).isFalse();
    assertThat(result.get(clearing.getId()).eligible()).isTrue();
    assertThat(result.get(clearing.getId()).clearedChequeId()).isEqualTo(cheque.getId());
  }
}
