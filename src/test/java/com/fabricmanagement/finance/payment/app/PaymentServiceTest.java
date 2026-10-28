package com.fabricmanagement.finance.payment.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.common.app.FinanceDocumentNumberGenerator;
import com.fabricmanagement.finance.common.app.FinancePostingDates;
import com.fabricmanagement.finance.common.app.SettlementFxResult;
import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import com.fabricmanagement.finance.common.exception.FuturePostingDateException;
import com.fabricmanagement.finance.payment.app.port.InvoiceAllocationView;
import com.fabricmanagement.finance.payment.app.port.InvoicePaymentPort;
import com.fabricmanagement.finance.payment.app.port.PaymentBindingPort;
import com.fabricmanagement.finance.payment.domain.Payment;
import com.fabricmanagement.finance.payment.domain.PaymentAllocation;
import com.fabricmanagement.finance.payment.domain.PaymentDirection;
import com.fabricmanagement.finance.payment.domain.PaymentMethod;
import com.fabricmanagement.finance.payment.domain.event.PaymentAllocatedEvent;
import com.fabricmanagement.finance.payment.domain.event.PaymentReversedEvent;
import com.fabricmanagement.finance.payment.domain.event.PaymentVoidedEvent;
import com.fabricmanagement.finance.payment.domain.exception.PaymentHeldForRefundException;
import com.fabricmanagement.finance.payment.domain.exception.PaymentReversalException;
import com.fabricmanagement.finance.payment.dto.CreateAllocationRequest;
import com.fabricmanagement.finance.payment.dto.CreatePaymentRequest;
import com.fabricmanagement.finance.payment.dto.PaymentDto;
import com.fabricmanagement.finance.payment.dto.ReversePaymentRequest;
import com.fabricmanagement.finance.payment.infra.repository.PaymentAllocationRepository;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import com.fabricmanagement.finance.payment.mapper.PaymentMapper;
import com.fabricmanagement.finance.period.app.port.FinancialPeriodGuard;
import com.fabricmanagement.finance.period.domain.exception.ClosedFinancialPeriodException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @Mock private PaymentRepository paymentRepository;
  @Mock private PaymentAllocationRepository paymentAllocationRepository;
  @Mock private InvoicePaymentPort invoicePaymentPort;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private FinanceDocumentNumberGenerator documentNumberGenerator;
  @Mock private FinancialPeriodGuard financialPeriodGuard;
  @Mock private PaymentBindingPort paymentBindings;
  @Mock private FinancePostingDates postingDates;
  @Spy private PaymentMapper paymentMapper = Mappers.getMapper(PaymentMapper.class);

  @InjectMocks private PaymentService paymentService;

  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void testAllocatePayment_Success() {
    UUID paymentId = UUID.randomUUID();
    UUID invoiceId = UUID.randomUUID();

    Payment payment =
        Payment.builder()
            .tradingPartnerId(UUID.randomUUID())
            .paymentNumber("PAY-1")
            .direction(PaymentDirection.INBOUND)
            .amount(Money.of(new java.math.BigDecimal("100.00"), "GBP"))
            .paymentDate(LocalDate.now())
            .allocations(new ArrayList<>())
            .build();
    payment.setId(paymentId);

    when(paymentRepository.findByTenantIdAndId(tenantId, paymentId))
        .thenReturn(Optional.of(payment));
    when(paymentRepository.saveAndFlush(payment))
        .thenAnswer(
            invocation -> {
              payment
                  .getAllocations()
                  .forEach(
                      allocation -> {
                        if (allocation.getId() == null) {
                          allocation.setId(UUID.randomUUID());
                        }
                      });
              return payment;
            });
    when(paymentAllocationRepository
            .findFirstByTenantIdAndPaymentIdAndInvoiceIdAndIsActiveTrueOrderByCreatedAtDesc(
                eq(tenantId), eq(paymentId), eq(invoiceId)))
        .thenAnswer(
            invocation ->
                payment.getAllocations().stream()
                    .filter(allocation -> allocation.getId() != null)
                    .findFirst());
    when(invoicePaymentPort.getInvoiceForAllocation(tenantId, invoiceId))
        .thenReturn(
            new InvoiceAllocationView(
                invoiceId, Money.of(new java.math.BigDecimal("50.00"), "GBP"), "GBP", true));
    when(invoicePaymentPort.recordAllocationFx(
            eq(tenantId),
            eq(invoiceId),
            any(UUID.class),
            eq(Money.of(new java.math.BigDecimal("40.00"), "GBP")),
            any()))
        .thenReturn(SettlementFxResult.zero("GBP", BigDecimal.ONE, LocalDate.now()));

    CreateAllocationRequest req = new CreateAllocationRequest(invoiceId, new BigDecimal("40.00"));

    paymentService.allocatePayment(paymentId, req);

    verify(financialPeriodGuard).assertPostingAllowed(tenantId, payment.getPaymentDate());
    verify(invoicePaymentPort)
        .applyAllocation(
            eq(tenantId),
            eq(invoiceId),
            eq(Money.of(new java.math.BigDecimal("40.00"), "GBP")),
            any());
    verify(paymentRepository).saveAndFlush(payment);
    verify(eventPublisher).publish(any(PaymentAllocatedEvent.class));

    assertThat(payment.getAllocatedAmount())
        .isEqualTo(Money.of(new java.math.BigDecimal("40.00"), "GBP"));
  }

  @Test
  void allocatePayment_rejectsClosedPaymentDateBeforeBusinessMutation() {
    UUID paymentId = UUID.randomUUID();
    UUID invoiceId = UUID.randomUUID();
    LocalDate paymentDate = LocalDate.of(2026, 5, 20);

    Payment payment =
        Payment.builder()
            .tradingPartnerId(UUID.randomUUID())
            .paymentNumber("PAY-1")
            .direction(PaymentDirection.INBOUND)
            .amount(Money.of(new BigDecimal("100.00"), "GBP"))
            .paymentDate(paymentDate)
            .allocations(new ArrayList<>())
            .build();
    payment.setId(paymentId);

    when(paymentRepository.findByTenantIdAndId(tenantId, paymentId))
        .thenReturn(Optional.of(payment));
    doThrow(new ClosedFinancialPeriodException(paymentDate, YearMonth.of(2026, 5)))
        .when(financialPeriodGuard)
        .assertPostingAllowed(tenantId, paymentDate);

    CreateAllocationRequest req = new CreateAllocationRequest(invoiceId, new BigDecimal("40.00"));

    assertThatThrownBy(() -> paymentService.allocatePayment(paymentId, req))
        .isInstanceOf(ClosedFinancialPeriodException.class);

    assertThat(payment.getAllocations()).isEmpty();
    verify(invoicePaymentPort, never()).getInvoiceForAllocation(any(), any());
    verify(paymentRepository, never()).saveAndFlush(any());
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void createPaymentWithAllocationsRejectsClosedPaymentDateBeforeNumberOrSave() {
    UUID invoiceId = UUID.randomUUID();
    LocalDate paymentDate = LocalDate.of(2026, 5, 20);
    CreatePaymentRequest request =
        new CreatePaymentRequest(
            UUID.randomUUID(),
            PaymentDirection.INBOUND.name(),
            "BANK_TRANSFER",
            new BigDecimal("100.00"),
            "GBP",
            paymentDate,
            "BANK-1",
            null,
            List.of(new CreateAllocationRequest(invoiceId, new BigDecimal("40.00"))));

    doThrow(new ClosedFinancialPeriodException(paymentDate, YearMonth.of(2026, 5)))
        .when(financialPeriodGuard)
        .assertPostingAllowed(tenantId, paymentDate);

    assertThatThrownBy(() -> paymentService.createPayment(request))
        .isInstanceOf(ClosedFinancialPeriodException.class);

    verify(documentNumberGenerator, never())
        .nextNumber(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    verify(paymentRepository, never()).save(any());
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void testDeallocatePayment_Success() {
    UUID paymentId = UUID.randomUUID();
    UUID invoiceId = UUID.randomUUID();

    Payment payment =
        Payment.builder()
            .tradingPartnerId(UUID.randomUUID())
            .paymentNumber("PAY-1")
            .direction(PaymentDirection.INBOUND)
            .amount(Money.of(new java.math.BigDecimal("100.00"), "GBP"))
            .paymentDate(LocalDate.now())
            .allocations(new ArrayList<>())
            .build();
    payment.setId(paymentId);

    PaymentAllocation allocation =
        payment.allocate(
            invoiceId,
            Money.of(new java.math.BigDecimal("40.00"), "GBP"),
            Money.of(new java.math.BigDecimal("50.00"), "GBP"),
            "GBP");
    UUID allocationId = UUID.randomUUID();
    allocation.setId(allocationId);

    when(paymentRepository.findByTenantIdAndId(tenantId, paymentId))
        .thenReturn(Optional.of(payment));

    paymentService.deallocatePayment(paymentId, allocationId);

    verify(financialPeriodGuard).assertPostingAllowed(tenantId, payment.getPaymentDate());
    verify(invoicePaymentPort)
        .reverseAllocation(
            eq(tenantId), eq(invoiceId), eq(Money.of(new java.math.BigDecimal("40.00"), "GBP")));
    verify(invoicePaymentPort).reverseAllocationFx(tenantId, allocationId);
    verify(paymentRepository).save(payment);

    assertThat(allocation.getIsActive()).isFalse();
    assertThat(payment.getAllocatedAmount()).isEqualTo(Money.zero("GBP"));
  }

  @Test
  void closedPeriodRejectsLegacyVoidBeforePaymentStateChanges() {
    UUID id = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 10, 20);
    Payment payment =
        Payment.builder()
            .tradingPartnerId(UUID.randomUUID())
            .paymentNumber("PAY-GB-1")
            .direction(PaymentDirection.INBOUND)
            .amount(Money.of(new BigDecimal("100.00"), "GBP"))
            .paymentDate(date)
            .build();
    payment.setId(id);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(payment));
    doThrow(new ClosedFinancialPeriodException(date, YearMonth.from(date)))
        .when(financialPeriodGuard)
        .assertPostingAllowed(tenantId, date);
    assertThatThrownBy(() -> paymentService.voidPayment(id))
        .isInstanceOf(ClosedFinancialPeriodException.class);
    assertThat(payment.getStatus())
        .isEqualTo(com.fabricmanagement.finance.payment.domain.PaymentStatus.RECEIVED);
    verify(paymentRepository, never()).save(any());
  }

  private Payment recorded(UUID id, LocalDate date, PaymentMethod method) {
    Payment payment =
        Payment.builder()
            .tradingPartnerId(UUID.randomUUID())
            .paymentNumber("PAY-2026-0001")
            .direction(PaymentDirection.INBOUND)
            .method(method)
            .amount(Money.of(new BigDecimal("100.00"), "GBP"))
            .paymentDate(date)
            .build();
    payment.setId(id);
    return payment;
  }

  private void postsWithIds(UUID reversalId) {
    when(paymentRepository.saveAndFlush(any(Payment.class)))
        .thenAnswer(
            call -> {
              Payment value = call.getArgument(0);
              if (value.getId() == null) value.setId(reversalId);
              return value;
            });
  }

  private static void assertRefused(ThrowingCallable call, String code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            PaymentReversalException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
  }

  @ParameterizedTest
  @EnumSource(PaymentMethod.class)
  void reverse_postsAnOppositePaymentForEveryMethodInAnOpenPeriod(PaymentMethod method) {
    UUID id = UUID.randomUUID(), reversalId = UUID.randomUUID(), actor = UUID.randomUUID();
    LocalDate paid = LocalDate.of(2026, 3, 10), on = LocalDate.of(2026, 10, 20);
    Payment original = recorded(id, paid, method);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(original));
    when(documentNumberGenerator.nextNumber(tenantId, "PAY", 2026)).thenReturn("PAY-2026-0009");
    postsWithIds(reversalId);

    PaymentDto reversal =
        paymentService.reverse(id, new ReversePaymentRequest(on, "Paid twice", null), actor);

    assertThat(reversal.id()).isEqualTo(reversalId);
    assertThat(reversal.direction()).isEqualTo("OUTBOUND");
    assertThat(reversal.method()).isEqualTo(method.name());
    assertThat(reversal.status()).isEqualTo("RECEIVED");
    assertThat(reversal.amount()).isEqualByComparingTo("100.00");
    assertThat(reversal.paymentDate()).isEqualTo(on);
    assertThat(original.getReversedByPaymentId()).isEqualTo(reversalId);
    assertThat(original.getStatus())
        .isEqualTo(com.fabricmanagement.finance.payment.domain.PaymentStatus.RECEIVED);
    // The period that matters is the one the reversal is posted in, not the original's.
    verify(paymentRepository).lockFinanceTenant(tenantId);
    verify(financialPeriodGuard).assertPostingAllowed(tenantId, on);
    verify(financialPeriodGuard, never()).assertPostingAllowed(tenantId, paid);
    verify(paymentBindings).releaseMatches(tenantId, id, on, actor, "Paid twice");
    verifyNoInteractions(invoicePaymentPort);
    ArgumentCaptor<PaymentReversedEvent> event =
        ArgumentCaptor.forClass(PaymentReversedEvent.class);
    verify(eventPublisher).publish(event.capture());
    assertThat(event.getValue().getPaymentId()).isEqualTo(id);
    assertThat(event.getValue().getReversalPaymentId()).isEqualTo(reversalId);
    assertThat(event.getValue().getReversalDate()).isEqualTo(on);
    assertThat(event.getValue().getDirection()).isEqualTo(PaymentDirection.INBOUND);
    assertThat(event.getValue().getAffectedInvoiceIds()).isEmpty();
  }

  @Test
  void reverse_releasesInvoiceAllocationsOnTheInvoicesAndReportsThem() {
    UUID id = UUID.randomUUID(), reversalId = UUID.randomUUID(), actor = UUID.randomUUID();
    UUID invoiceId = UUID.randomUUID();
    LocalDate on = LocalDate.of(2026, 10, 20);
    Payment original = recorded(id, LocalDate.of(2026, 3, 10), PaymentMethod.BANK_TRANSFER);
    PaymentAllocation allocation =
        original.allocate(
            invoiceId,
            Money.of(new BigDecimal("40.00"), "GBP"),
            Money.of(new BigDecimal("100.00"), "GBP"),
            "GBP");
    allocation.setId(UUID.randomUUID());
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(original));
    when(documentNumberGenerator.nextNumber(tenantId, "PAY", 2026)).thenReturn("PAY-2026-0009");
    postsWithIds(reversalId);

    paymentService.reverse(id, new ReversePaymentRequest(on, "Wrong customer", null), actor);

    assertThat(allocation.getIsActive()).isFalse();
    verify(invoicePaymentPort)
        .reverseAllocation(tenantId, invoiceId, Money.of(new BigDecimal("40.00"), "GBP"));
    verify(invoicePaymentPort).reverseAllocationFx(tenantId, allocation.getId());
    ArgumentCaptor<PaymentReversedEvent> event =
        ArgumentCaptor.forClass(PaymentReversedEvent.class);
    verify(eventPublisher).publish(event.capture());
    assertThat(event.getValue().getAffectedInvoiceIds()).containsExactly(invoiceId);
  }

  @Test
  void reverse_isRefusedInAClosedPeriodBeforeAnythingIsNumberedOrSaved() {
    UUID id = UUID.randomUUID();
    LocalDate on = LocalDate.of(2026, 5, 20);
    Payment original = recorded(id, LocalDate.of(2026, 5, 2), PaymentMethod.CASH);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(original));
    doThrow(new ClosedFinancialPeriodException(on, YearMonth.from(on)))
        .when(financialPeriodGuard)
        .assertPostingAllowed(tenantId, on);
    var request = new ReversePaymentRequest(on, "Late", null);

    assertThatThrownBy(() -> paymentService.reverse(id, request, UUID.randomUUID()))
        .isInstanceOf(ClosedFinancialPeriodException.class);

    assertThat(original.isReversed()).isFalse();
    verifyNoInteractions(documentNumberGenerator, paymentBindings, invoicePaymentPort);
    verify(paymentRepository, never()).saveAndFlush(any());
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void reverse_isRefusedWhenDatedAfterTheTenantsToday() {
    UUID id = UUID.randomUUID();
    LocalDate tomorrow = LocalDate.of(2026, 10, 23);
    Payment original = recorded(id, LocalDate.of(2026, 10, 1), PaymentMethod.CASH);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(original));
    doThrow(new FuturePostingDateException(tomorrow, LocalDate.of(2026, 10, 22)))
        .when(postingDates)
        .assertNotFuture(tomorrow);
    var request = new ReversePaymentRequest(tomorrow, "Too early", null);

    assertThatThrownBy(() -> paymentService.reverse(id, request, UUID.randomUUID()))
        .isInstanceOf(FuturePostingDateException.class);

    assertThat(original.isReversed()).isFalse();
    verifyNoInteractions(documentNumberGenerator, paymentBindings, invoicePaymentPort);
    verify(financialPeriodGuard, never()).assertPostingAllowed(any(), any());
    verify(paymentRepository, never()).saveAndFlush(any());
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void createPaymentIsRefusedWhenDatedAfterTheTenantsToday() {
    LocalDate tomorrow = LocalDate.of(2026, 10, 23);
    CreatePaymentRequest request =
        new CreatePaymentRequest(
            UUID.randomUUID(),
            PaymentDirection.INBOUND.name(),
            "BANK_TRANSFER",
            new BigDecimal("100.00"),
            "GBP",
            tomorrow,
            "BANK-1",
            null,
            List.of());
    doThrow(new FuturePostingDateException(tomorrow, LocalDate.of(2026, 10, 22)))
        .when(postingDates)
        .assertNotFuture(tomorrow);

    assertThatThrownBy(() -> paymentService.createPayment(request))
        .isInstanceOf(FuturePostingDateException.class);

    verify(financialPeriodGuard, never()).assertPostingAllowed(any(), any());
    verify(documentNumberGenerator, never())
        .nextNumber(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    verify(paymentRepository, never()).save(any());
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void reverse_isRefusedBeforeAnythingIsReleasedWhenDatedBeforeAMatchItMustUndo() {
    UUID id = UUID.randomUUID();
    LocalDate on = LocalDate.of(2026, 10, 1);
    Payment original = recorded(id, LocalDate.of(2026, 9, 20), PaymentMethod.CASH);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(original));
    doThrow(new FinanceDomainException("The date precedes the match it undoes"))
        .when(paymentBindings)
        .assertMatchesReleasableOn(tenantId, id, on);
    var request = new ReversePaymentRequest(on, "Early", null);

    assertThatThrownBy(() -> paymentService.reverse(id, request, UUID.randomUUID()))
        .isInstanceOf(FinanceDomainException.class);

    // No number was drawn, no allocation released, nothing posted: the check runs first.
    assertThat(original.isReversed()).isFalse();
    verifyNoInteractions(documentNumberGenerator, invoicePaymentPort);
    verify(paymentBindings, never()).releaseMatches(any(), any(), any(), any(), any());
    verify(paymentRepository, never()).saveAndFlush(any());
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void reverse_isRefusedBeforeAnythingIsReleasedWhenDatedBeforeTheHistoryOfARefundItMoves() {
    UUID id = UUID.randomUUID();
    LocalDate on = LocalDate.of(2026, 10, 1);
    Payment original = recorded(id, LocalDate.of(2026, 9, 20), PaymentMethod.CASH);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(original));
    doThrow(new FinanceDomainException("The date precedes the refund's history"))
        .when(paymentBindings)
        .assertRefundsReleasableOn(tenantId, id, on);
    var request = new ReversePaymentRequest(on, "Early", null);

    assertThatThrownBy(() -> paymentService.reverse(id, request, UUID.randomUUID()))
        .isInstanceOf(FinanceDomainException.class);

    // Nothing was drawn, released, reopened or cancelled: the check runs before any of it.
    assertThat(original.isReversed()).isFalse();
    verifyNoInteractions(documentNumberGenerator, invoicePaymentPort);
    verify(paymentBindings, never()).releaseMatches(any(), any(), any(), any(), any());
    verify(paymentBindings, never()).cancelUnpaidRefunds(any(), any(), any(), any(), any());
    verify(paymentBindings, never()).reopenRefunds(any(), any(), any(), any(), any());
    verify(paymentRepository, never()).saveAndFlush(any());
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void reverseReleasedInstrument_checksTheRefundHistoryBeforeCancellingAnyRefund() {
    UUID id = UUID.randomUUID(), reversalId = UUID.randomUUID(), actor = UUID.randomUUID();
    LocalDate on = LocalDate.of(2026, 10, 20);
    Payment original = recorded(id, LocalDate.of(2026, 10, 2), PaymentMethod.CHEQUE);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(original));
    when(documentNumberGenerator.nextNumber(tenantId, "PAY", 2026)).thenReturn("PAY-2026-0009");
    postsWithIds(reversalId);

    paymentService.reverseReleasedInstrument(
        id, new ReversePaymentRequest(on, "Cheque bounced", null), actor);

    var order = inOrder(paymentBindings, documentNumberGenerator);
    order.verify(paymentBindings).assertMatchesReleasableOn(tenantId, id, on);
    order.verify(paymentBindings).assertRefundsReleasableOn(tenantId, id, on);
    order.verify(documentNumberGenerator).nextNumber(tenantId, "PAY", 2026);
    order.verify(paymentBindings).cancelUnpaidRefunds(tenantId, id, on, actor, "Cheque bounced");
  }

  @Test
  void reverse_isRefusedForAPaymentBelongingToAChequeClearing() {
    UUID id = UUID.randomUUID();
    LocalDate on = LocalDate.of(2026, 10, 20);
    Payment original = recorded(id, LocalDate.of(2026, 10, 2), PaymentMethod.CHEQUE);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(original));
    when(paymentBindings.isBoundToInstrument(tenantId, id)).thenReturn(true);
    var request = new ReversePaymentRequest(on, "Bounced", null);

    assertRefused(
        () -> paymentService.reverse(id, request, UUID.randomUUID()),
        "PAYMENT_BOUND_TO_INSTRUMENT");

    assertThat(original.isReversed()).isFalse();
    verifyNoInteractions(documentNumberGenerator, invoicePaymentPort);
    verify(paymentBindings, never()).releaseMatches(any(), any(), any(), any(), any());
    verify(paymentRepository, never()).saveAndFlush(any());
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void reverseReleasedInstrument_postsTheReversalForTheChequeFlowWithoutAskingAboutTheClearing() {
    UUID id = UUID.randomUUID(), reversalId = UUID.randomUUID(), actor = UUID.randomUUID();
    LocalDate on = LocalDate.of(2026, 10, 20);
    Payment original = recorded(id, LocalDate.of(2026, 10, 2), PaymentMethod.CHEQUE);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(original));
    when(documentNumberGenerator.nextNumber(tenantId, "PAY", 2026)).thenReturn("PAY-2026-0009");
    postsWithIds(reversalId);

    PaymentDto reversal =
        paymentService.reverseReleasedInstrument(
            id, new ReversePaymentRequest(on, "Cheque bounced", null), actor);

    assertThat(reversal.method()).isEqualTo("CHEQUE");
    assertThat(reversal.direction()).isEqualTo("OUTBOUND");
    assertThat(original.getReversedByPaymentId()).isEqualTo(reversalId);
    verify(paymentBindings, never()).isBoundToInstrument(any(), any());
    verify(paymentBindings).releaseMatches(tenantId, id, on, actor, "Cheque bounced");
  }

  @Test
  void reverse_isRefusedForAPaymentThatIsAlreadyReversedAndReleasesNothing() {
    UUID id = UUID.randomUUID();
    LocalDate on = LocalDate.of(2026, 10, 20);
    Payment original = recorded(id, LocalDate.of(2026, 10, 2), PaymentMethod.BANK_TRANSFER);
    original.markReversedBy(UUID.randomUUID());
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(original));
    when(documentNumberGenerator.nextNumber(tenantId, "PAY", 2026)).thenReturn("PAY-2026-0009");
    var request = new ReversePaymentRequest(on, "Again", null);

    assertRefused(
        () -> paymentService.reverse(id, request, UUID.randomUUID()), "PAYMENT_ALREADY_REVERSED");

    verify(paymentBindings, never()).releaseMatches(any(), any(), any(), any(), any());
    verifyNoInteractions(invoicePaymentPort);
    verify(paymentRepository, never()).saveAndFlush(any());
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void reverse_needsTheActingUser() {
    UUID id = UUID.randomUUID();
    var request = new ReversePaymentRequest(LocalDate.of(2026, 10, 20), "Anything", null);

    assertThatThrownBy(() -> paymentService.reverse(id, request, null))
        .isInstanceOf(IllegalArgumentException.class);

    verifyNoInteractions(paymentRepository, paymentBindings);
  }

  @Test
  void voidPayment_isRefusedForAPaymentWithAllocationsAndLeavesTheInvoicesAlone() {
    UUID id = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 10, 20);
    Payment payment = recorded(id, date, PaymentMethod.BANK_TRANSFER);
    payment.allocate(
        UUID.randomUUID(),
        Money.of(new BigDecimal("40.00"), "GBP"),
        Money.of(new BigDecimal("100.00"), "GBP"),
        "GBP");
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(payment));

    assertRefused(() -> paymentService.voidPayment(id), "PAYMENT_VOID_HAS_ALLOCATIONS");

    assertThat(payment.getStatus())
        .isEqualTo(com.fabricmanagement.finance.payment.domain.PaymentStatus.RECEIVED);
    verifyNoInteractions(invoicePaymentPort);
    verify(paymentRepository, never()).save(any());
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void voidPayment_stillVoidsAnUnallocatedPaymentInAnOpenPeriod() {
    UUID id = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 10, 20);
    Payment payment = recorded(id, date, PaymentMethod.CASH);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(payment));

    PaymentDto voided = paymentService.voidPayment(id);

    assertThat(voided.status()).isEqualTo("VOIDED");
    verify(financialPeriodGuard).assertPostingAllowed(tenantId, date);
    verify(eventPublisher).publish(any(PaymentVoidedEvent.class));
  }

  @Test
  void voidPayment_isRefusedForTheClearingPaymentOfACheque() {
    UUID id = UUID.randomUUID();
    Payment payment = recorded(id, LocalDate.of(2026, 10, 20), PaymentMethod.CHEQUE);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(payment));
    when(paymentBindings.isBoundToInstrument(tenantId, id)).thenReturn(true);

    assertRefused(() -> paymentService.voidPayment(id), "PAYMENT_BOUND_TO_INSTRUMENT");

    assertThat(payment.getStatus())
        .isEqualTo(com.fabricmanagement.finance.payment.domain.PaymentStatus.RECEIVED);
    verify(paymentBindings, never()).reopenRefunds(any(), any(), any(), any(), any());
    verify(paymentRepository, never()).save(any());
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void voidPayment_isRefusedForAPaymentMatchedToACollectionExpectation() {
    UUID id = UUID.randomUUID();
    Payment payment = recorded(id, LocalDate.of(2026, 10, 20), PaymentMethod.BANK_TRANSFER);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(payment));
    when(paymentBindings.hasActiveMatches(tenantId, id)).thenReturn(true);

    assertRefused(() -> paymentService.voidPayment(id), "PAYMENT_VOID_HAS_MATCHES");

    assertThat(payment.getStatus())
        .isEqualTo(com.fabricmanagement.finance.payment.domain.PaymentStatus.RECEIVED);
    verify(paymentBindings, never()).reopenRefunds(any(), any(), any(), any(), any());
    verify(paymentRepository, never()).save(any());
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void allocatePayment_isRefusedForAPaymentThatPaysARefund() {
    UUID paymentId = UUID.randomUUID(), invoiceId = UUID.randomUUID();
    Payment payment = recorded(paymentId, LocalDate.of(2026, 10, 20), PaymentMethod.BANK_TRANSFER);
    when(paymentRepository.findByTenantIdAndId(tenantId, paymentId))
        .thenReturn(Optional.of(payment));
    when(paymentBindings.settlesRefund(tenantId, paymentId)).thenReturn(true);
    var request = new CreateAllocationRequest(invoiceId, new BigDecimal("40.00"));

    assertThatThrownBy(() -> paymentService.allocatePayment(paymentId, request))
        .isInstanceOfSatisfying(
            PaymentHeldForRefundException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("PAYMENT_SETTLES_REFUND"));

    assertThat(payment.getAllocatedAmount().getAmount()).isEqualByComparingTo("0");
    verifyNoInteractions(invoicePaymentPort);
    verify(paymentRepository, never()).saveAndFlush(any());
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void reverseReleasedInstrument_cancelsTheRefundsNobodyPaidBecauseTheMoneyNeverCame() {
    UUID id = UUID.randomUUID(), reversalId = UUID.randomUUID(), actor = UUID.randomUUID();
    LocalDate on = LocalDate.of(2026, 10, 20);
    Payment original = recorded(id, LocalDate.of(2026, 10, 2), PaymentMethod.CHEQUE);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(original));
    when(documentNumberGenerator.nextNumber(tenantId, "PAY", 2026)).thenReturn("PAY-2026-0009");
    postsWithIds(reversalId);

    paymentService.reverseReleasedInstrument(
        id, new ReversePaymentRequest(on, "Cheque bounced", null), actor);

    var order = inOrder(paymentBindings);
    order.verify(paymentBindings).releaseMatches(tenantId, id, on, actor, "Cheque bounced");
    order.verify(paymentBindings).cancelUnpaidRefunds(tenantId, id, on, actor, "Cheque bounced");
    order.verify(paymentBindings).reopenRefunds(tenantId, id, on, actor, "Cheque bounced");
    verify(paymentBindings, never()).holdsRefund(any(), any());
  }

  @Test
  void reverse_leavesRefundsToTheirOwnCancellation() {
    UUID id = UUID.randomUUID(), reversalId = UUID.randomUUID(), actor = UUID.randomUUID();
    LocalDate on = LocalDate.of(2026, 10, 20);
    Payment original = recorded(id, LocalDate.of(2026, 10, 2), PaymentMethod.BANK_TRANSFER);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(original));
    when(documentNumberGenerator.nextNumber(tenantId, "PAY", 2026)).thenReturn("PAY-2026-0009");
    postsWithIds(reversalId);

    paymentService.reverse(id, new ReversePaymentRequest(on, "Sent in error", null), actor);

    verify(paymentBindings, never()).cancelUnpaidRefunds(any(), any(), any(), any(), any());
  }

  private void holdsForRefund(UUID id, String amount) {
    holdsForRefund(id, amount, "0.00");
  }

  /** {@code held} is owed back in all, of which {@code paid} has already left the business. */
  private void holdsForRefund(UUID id, String held, String paid) {
    when(paymentBindings.holdsRefund(tenantId, id)).thenReturn(true);
    when(paymentBindings.refundHeld(tenantId, id)).thenReturn(new BigDecimal(held));
    // Only the reversal asks how much of it is paid; voiding and allocating never do.
    lenient().when(paymentBindings.refundPaid(tenantId, id)).thenReturn(new BigDecimal(paid));
  }

  private static void assertHeld(ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            PaymentHeldForRefundException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("PAYMENT_HELD_FOR_REFUND"));
  }

  @Test
  void voidPayment_isRefusedWhileTheCustomerIsOwedARefundFromIt() {
    UUID id = UUID.randomUUID();
    Payment payment = recorded(id, LocalDate.of(2026, 10, 20), PaymentMethod.BANK_TRANSFER);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(payment));
    holdsForRefund(id, "30.00");

    assertHeld(() -> paymentService.voidPayment(id));

    assertThat(payment.getStatus())
        .isEqualTo(com.fabricmanagement.finance.payment.domain.PaymentStatus.RECEIVED);
    verify(paymentBindings, never()).reopenRefunds(any(), any(), any(), any(), any());
    verify(paymentRepository, never()).save(any());
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void reverse_isRefusedWhileTheCustomerIsOwedARefundFromThePayment() {
    UUID id = UUID.randomUUID();
    LocalDate on = LocalDate.of(2026, 10, 20);
    Payment original = recorded(id, LocalDate.of(2026, 10, 2), PaymentMethod.BANK_TRANSFER);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(original));
    holdsForRefund(id, "30.00");
    var request = new ReversePaymentRequest(on, "Paid twice", null);

    assertHeld(() -> paymentService.reverse(id, request, UUID.randomUUID()));

    assertThat(original.isReversed()).isFalse();
    verifyNoInteractions(documentNumberGenerator, invoicePaymentPort);
    verify(paymentBindings, never()).releaseMatches(any(), any(), any(), any(), any());
    verify(paymentBindings, never()).reopenRefunds(any(), any(), any(), any(), any());
    verify(paymentRepository, never()).saveAndFlush(any());
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void reverse_isStillRefusedWhileOnlyPartOfTheRefundIsPaid() {
    UUID id = UUID.randomUUID();
    LocalDate on = LocalDate.of(2026, 10, 20);
    Payment original = recorded(id, LocalDate.of(2026, 10, 2), PaymentMethod.BANK_TRANSFER);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(original));
    holdsForRefund(id, "50.00", "20.00");
    // Confirming what was paid does not settle the 30.00 that is still owed.
    var request = new ReversePaymentRequest(on, "Paid twice", new BigDecimal("20.00"));

    assertThatThrownBy(() -> paymentService.reverse(id, request, UUID.randomUUID()))
        .isInstanceOfSatisfying(
            PaymentHeldForRefundException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("PAYMENT_HELD_FOR_REFUND");
              assertThat(e.getMessage()).contains("30.00");
            });

    assertThat(original.isReversed()).isFalse();
    verifyNoInteractions(documentNumberGenerator, invoicePaymentPort);
    verify(paymentBindings, never()).releaseMatches(any(), any(), any(), any(), any());
  }

  @Test
  void reverse_asksForTheAmountOfRefundsAlreadyPaidBeforeTakingTheReceiptBack() {
    UUID id = UUID.randomUUID();
    LocalDate on = LocalDate.of(2026, 10, 20);
    Payment original = recorded(id, LocalDate.of(2026, 10, 2), PaymentMethod.BANK_TRANSFER);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(original));
    holdsForRefund(id, "30.00", "30.00");
    var request = new ReversePaymentRequest(on, "Paid twice", null);

    assertThatThrownBy(() -> paymentService.reverse(id, request, UUID.randomUUID()))
        .isInstanceOfSatisfying(
            PaymentHeldForRefundException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("PAYMENT_KEEPS_PAID_REFUND");
              assertThat(e.getHttpStatus()).isEqualTo(409);
              assertThat(e.getArgs()).containsExactly("30.00");
              assertThat(e.getDetails()).containsEntry("paidRefund", "30.00");
            });

    assertThat(original.isReversed()).isFalse();
    verifyNoInteractions(documentNumberGenerator, invoicePaymentPort);
    verify(paymentBindings, never()).releaseMatches(any(), any(), any(), any(), any());
    verify(paymentBindings, never()).reopenRefunds(any(), any(), any(), any(), any());
    verify(paymentRepository, never()).saveAndFlush(any());
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void reverse_refusesAConfirmationOfAnAmountThatIsNoLongerWhatWasPaid() {
    UUID id = UUID.randomUUID();
    LocalDate on = LocalDate.of(2026, 10, 20);
    Payment original = recorded(id, LocalDate.of(2026, 10, 2), PaymentMethod.BANK_TRANSFER);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(original));
    // Another refund was paid after the screen was read: 30.00 was confirmed, 50.00 is paid now.
    holdsForRefund(id, "50.00", "50.00");
    var request = new ReversePaymentRequest(on, "Paid twice", new BigDecimal("30.00"));

    assertThatThrownBy(() -> paymentService.reverse(id, request, UUID.randomUUID()))
        .isInstanceOfSatisfying(
            PaymentHeldForRefundException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("PAYMENT_KEEPS_PAID_REFUND");
              assertThat(e.getArgs()).containsExactly("50.00");
            });

    assertThat(original.isReversed()).isFalse();
    verifyNoInteractions(documentNumberGenerator);
  }

  @Test
  void reverse_leavesRefundsAlreadyPaidAsRecordedOnceTheAmountIsConfirmed() {
    UUID id = UUID.randomUUID(), reversalId = UUID.randomUUID(), actor = UUID.randomUUID();
    LocalDate on = LocalDate.of(2026, 10, 20);
    Payment original = recorded(id, LocalDate.of(2026, 10, 2), PaymentMethod.BANK_TRANSFER);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(original));
    when(documentNumberGenerator.nextNumber(tenantId, "PAY", 2026)).thenReturn("PAY-2026-0009");
    postsWithIds(reversalId);
    holdsForRefund(id, "30.00", "30.00");

    // The scale of the confirmed amount does not matter: 30 is 30.00.
    paymentService.reverse(
        id, new ReversePaymentRequest(on, "Paid twice", new BigDecimal("30")), actor);

    assertThat(original.isReversed()).isTrue();
    var order = inOrder(paymentBindings);
    order.verify(paymentBindings).releaseMatches(tenantId, id, on, actor, "Paid twice");
    order.verify(paymentBindings).reopenRefunds(tenantId, id, on, actor, "Paid twice");
    // The money left, so the refund is neither cancelled nor undone; only unpaid ones are.
    verify(paymentBindings, never()).cancelUnpaidRefunds(any(), any(), any(), any(), any());
  }

  @Test
  void voidPayment_isRefusedOnceARefundWasPaidOutOfIt() {
    UUID id = UUID.randomUUID();
    Payment payment = recorded(id, LocalDate.of(2026, 10, 20), PaymentMethod.BANK_TRANSFER);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(payment));
    holdsForRefund(id, "30.00", "30.00");

    // A void leaves no dated posting, so it cannot keep money that already left: reverse instead.
    assertHeld(() -> paymentService.voidPayment(id));

    assertThat(payment.getStatus())
        .isEqualTo(com.fabricmanagement.finance.payment.domain.PaymentStatus.RECEIVED);
    verify(paymentRepository, never()).save(any());
  }

  @Test
  void voidPayment_reopensTheRefundsThisPaymentHadPaid() {
    UUID id = UUID.randomUUID(), actor = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 10, 20);
    Payment payment = recorded(id, date, PaymentMethod.BANK_TRANSFER);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(payment));
    TenantContext.setCurrentUserId(actor);

    paymentService.voidPayment(id);

    verify(paymentBindings).reopenRefunds(tenantId, id, date, actor, "Settling payment voided");
  }

  @Test
  void reverse_reopensTheRefundsThisPaymentHadPaidAtTheReversalDate() {
    UUID id = UUID.randomUUID(), reversalId = UUID.randomUUID(), actor = UUID.randomUUID();
    LocalDate on = LocalDate.of(2026, 10, 20);
    Payment original = recorded(id, LocalDate.of(2026, 10, 2), PaymentMethod.BANK_TRANSFER);
    when(paymentRepository.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(original));
    when(documentNumberGenerator.nextNumber(tenantId, "PAY", 2026)).thenReturn("PAY-2026-0009");
    postsWithIds(reversalId);

    paymentService.reverse(id, new ReversePaymentRequest(on, "Sent in error", null), actor);

    var order = inOrder(paymentBindings);
    order.verify(paymentBindings).releaseMatches(tenantId, id, on, actor, "Sent in error");
    order.verify(paymentBindings).reopenRefunds(tenantId, id, on, actor, "Sent in error");
  }

  @Test
  void allocatePayment_cannotSpendWhatIsSetAsideForARefund() {
    UUID paymentId = UUID.randomUUID(), invoiceId = UUID.randomUUID();
    Payment payment = recorded(paymentId, LocalDate.of(2026, 10, 20), PaymentMethod.CASH);
    when(paymentRepository.findByTenantIdAndId(tenantId, paymentId))
        .thenReturn(Optional.of(payment));
    when(invoicePaymentPort.getInvoiceForAllocation(tenantId, invoiceId))
        .thenReturn(
            new InvoiceAllocationView(
                invoiceId, Money.of(new BigDecimal("90.00"), "GBP"), "GBP", true));
    holdsForRefund(paymentId, "70.00");
    var request = new CreateAllocationRequest(invoiceId, new BigDecimal("40.00"));

    assertHeld(() -> paymentService.allocatePayment(paymentId, request));

    assertThat(payment.getAllocatedAmount().getAmount()).isEqualByComparingTo("0");
    verify(paymentRepository, never()).saveAndFlush(any());
    verify(invoicePaymentPort, never()).applyAllocation(any(), any(), any(), any());
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void allocatePayment_mayStillSpendExactlyWhatIsNotSetAside() {
    UUID paymentId = UUID.randomUUID(), invoiceId = UUID.randomUUID();
    Payment payment = recorded(paymentId, LocalDate.of(2026, 10, 20), PaymentMethod.CASH);
    when(paymentRepository.findByTenantIdAndId(tenantId, paymentId))
        .thenReturn(Optional.of(payment));
    when(paymentRepository.saveAndFlush(payment))
        .thenAnswer(
            call -> {
              payment.getAllocations().forEach(a -> a.setId(UUID.randomUUID()));
              return payment;
            });
    when(paymentAllocationRepository
            .findFirstByTenantIdAndPaymentIdAndInvoiceIdAndIsActiveTrueOrderByCreatedAtDesc(
                tenantId, paymentId, invoiceId))
        .thenAnswer(call -> payment.getAllocations().stream().findFirst());
    when(invoicePaymentPort.getInvoiceForAllocation(tenantId, invoiceId))
        .thenReturn(
            new InvoiceAllocationView(
                invoiceId, Money.of(new BigDecimal("90.00"), "GBP"), "GBP", true));
    when(invoicePaymentPort.recordAllocationFx(
            eq(tenantId), eq(invoiceId), any(UUID.class), any(), any()))
        .thenReturn(SettlementFxResult.zero("GBP", BigDecimal.ONE, LocalDate.of(2026, 10, 20)));
    holdsForRefund(paymentId, "60.00");

    paymentService.allocatePayment(
        paymentId, new CreateAllocationRequest(invoiceId, new BigDecimal("40.00")));

    assertThat(payment.getAllocatedAmount()).isEqualTo(Money.of(new BigDecimal("40.00"), "GBP"));
    verify(eventPublisher).publish(any(PaymentAllocatedEvent.class));
  }
}
