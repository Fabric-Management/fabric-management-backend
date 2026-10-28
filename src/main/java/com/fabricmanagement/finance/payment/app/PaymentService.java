package com.fabricmanagement.finance.payment.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.common.app.FinanceDocumentNumberGenerator;
import com.fabricmanagement.finance.common.app.FinancePostingDates;
import com.fabricmanagement.finance.common.app.SettlementFxResult;
import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import com.fabricmanagement.finance.payment.app.port.InvoiceAllocationView;
import com.fabricmanagement.finance.payment.app.port.InvoicePaymentPort;
import com.fabricmanagement.finance.payment.app.port.PaymentBindingPort;
import com.fabricmanagement.finance.payment.domain.Payment;
import com.fabricmanagement.finance.payment.domain.PaymentAllocation;
import com.fabricmanagement.finance.payment.domain.PaymentDirection;
import com.fabricmanagement.finance.payment.domain.PaymentMethod;
import com.fabricmanagement.finance.payment.domain.event.PaymentAllocatedEvent;
import com.fabricmanagement.finance.payment.domain.event.PaymentReceivedEvent;
import com.fabricmanagement.finance.payment.domain.event.PaymentReversedEvent;
import com.fabricmanagement.finance.payment.domain.event.PaymentVoidedEvent;
import com.fabricmanagement.finance.payment.domain.exception.PaymentHeldForRefundException;
import com.fabricmanagement.finance.payment.domain.exception.PaymentReversalException;
import com.fabricmanagement.finance.payment.dto.CreateAllocationRequest;
import com.fabricmanagement.finance.payment.dto.CreatePaymentRequest;
import com.fabricmanagement.finance.payment.dto.PaymentAllocationDto;
import com.fabricmanagement.finance.payment.dto.PaymentDto;
import com.fabricmanagement.finance.payment.dto.ReversePaymentRequest;
import com.fabricmanagement.finance.payment.infra.repository.PaymentAllocationRepository;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import com.fabricmanagement.finance.payment.mapper.PaymentMapper;
import com.fabricmanagement.finance.period.app.port.FinancialPeriodGuard;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing payments and invoice allocations.
 *
 * <p><b>Concurrency Guard:</b> Simultaneous allocations against the same invoice are protected by
 * the {@code BaseEntity.@Version} field on the Invoice. If two concurrent transactions attempt to
 * allocate the same open balance, the second to commit will throw an {@code
 * OptimisticLockException}. Clients should retry the operation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final PaymentMapper paymentMapper;
  private final FinanceDocumentNumberGenerator documentNumberGenerator;
  private final InvoicePaymentPort invoicePaymentPort;
  private final DomainEventPublisher eventPublisher;
  private final PaymentAllocationRepository paymentAllocationRepository;
  private final FinancialPeriodGuard financialPeriodGuard;
  private final PaymentBindingPort paymentBindings;
  private final FinancePostingDates postingDates;

  public PaymentDto createPayment(CreatePaymentRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    paymentRepository.lockFinanceTenant(tenantId);
    postingDates.assertNotFuture(request.paymentDate());
    financialPeriodGuard.assertPostingAllowed(tenantId, request.paymentDate());

    String paymentNumber =
        documentNumberGenerator.nextNumber(tenantId, "PAY", request.paymentDate().getYear());

    Payment payment =
        Payment.builder()
            .tradingPartnerId(request.tradingPartnerId())
            .paymentNumber(paymentNumber)
            .direction(PaymentDirection.valueOf(request.direction()))
            .method(PaymentMethod.valueOf(request.method()))
            .amount(Money.of(request.amount(), request.currency()))
            .paymentDate(request.paymentDate())
            .bankReference(request.bankReference())
            .notes(request.notes())
            .build();

    Payment saved = paymentRepository.save(payment);

    eventPublisher.publish(
        new PaymentReceivedEvent(
            tenantId,
            saved.getId(),
            saved.getPaymentNumber(),
            saved.getTradingPartnerId(),
            saved.getDirection(),
            saved.getAmount(),
            saved.getCurrency()));

    List<PaymentAllocationDto> allocationDtos = new ArrayList<>();
    if (request.allocations() != null && !request.allocations().isEmpty()) {
      for (CreateAllocationRequest allocRequest : request.allocations()) {
        allocationDtos.add(doAllocate(tenantId, saved, allocRequest));
      }
    }

    PaymentDto paymentDto = paymentMapper.toDto(saved);
    return allocationDtos.isEmpty() ? paymentDto : withAllocations(paymentDto, allocationDtos);
  }

  public PaymentAllocationDto allocatePayment(UUID paymentId, CreateAllocationRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    paymentRepository.lockFinanceTenant(tenantId);
    Payment payment = getPaymentOrThrow(tenantId, paymentId);
    return doAllocate(tenantId, payment, request);
  }

  private PaymentAllocationDto doAllocate(
      UUID tenantId, Payment payment, CreateAllocationRequest request) {
    financialPeriodGuard.assertPostingAllowed(tenantId, payment.getPaymentDate());
    if (paymentBindings.settlesRefund(tenantId, payment.getId()))
      throw PaymentHeldForRefundException.settlesRefund();

    InvoiceAllocationView invoiceView =
        invoicePaymentPort.getInvoiceForAllocation(tenantId, request.invoiceId());

    if (!invoiceView.payable()) {
      throw new FinanceDomainException("Invoice is not in a payable state");
    }

    Money allocationAmount = Money.of(request.amount(), invoiceView.currency());
    requireNotHeldForRefund(tenantId, payment, allocationAmount);

    PaymentAllocation allocation =
        payment.allocate(
            invoiceView.invoiceId(),
            allocationAmount,
            invoiceView.openBalance(),
            invoiceView.currency());

    paymentRepository.saveAndFlush(payment);
    allocation = findPersistedAllocation(tenantId, payment.getId(), allocation.getInvoiceId());

    invoicePaymentPort.applyAllocation(
        tenantId, invoiceView.invoiceId(), allocation.getAmount(), payment.getPaymentDate());

    SettlementFxResult fxResult =
        invoicePaymentPort.recordAllocationFx(
            tenantId,
            invoiceView.invoiceId(),
            allocation.getId(),
            allocation.getAmount(),
            payment.getPaymentDate());

    eventPublisher.publish(
        new PaymentAllocatedEvent(
            tenantId, payment.getId(), invoiceView.invoiceId(), allocationAmount));

    log.info(
        "Allocated {} from payment {} to invoice {}",
        allocationAmount,
        payment.getId(),
        request.invoiceId());
    return paymentMapper.toAllocationDto(allocation, fxResult);
  }

  private PaymentAllocation findPersistedAllocation(UUID tenantId, UUID paymentId, UUID invoiceId) {
    return paymentAllocationRepository
        .findFirstByTenantIdAndPaymentIdAndInvoiceIdAndIsActiveTrueOrderByCreatedAtDesc(
            tenantId, paymentId, invoiceId)
        .orElseThrow(
            () ->
                new FinanceDomainException(
                    "Persisted payment allocation could not be resolved for FX realization"));
  }

  public void deallocatePayment(UUID paymentId, UUID allocationId) {
    UUID tenantId = TenantContext.requireTenantId();
    paymentRepository.lockFinanceTenant(tenantId);
    Payment payment = getPaymentOrThrow(tenantId, paymentId);

    financialPeriodGuard.assertPostingAllowed(tenantId, payment.getPaymentDate());
    PaymentAllocation allocation = payment.deallocate(allocationId);

    invoicePaymentPort.reverseAllocation(
        tenantId, allocation.getInvoiceId(), allocation.getAmount());
    invoicePaymentPort.reverseAllocationFx(tenantId, allocation.getId());

    paymentRepository.save(payment);

    log.info(
        "Deallocated {} from payment {} (allocation: {})",
        allocation.getAmount(),
        paymentId,
        allocationId);
  }

  public PaymentDto voidPayment(UUID paymentId) {
    UUID tenantId = TenantContext.requireTenantId();
    paymentRepository.lockFinanceTenant(tenantId);
    Payment payment = getPaymentOrThrow(tenantId, paymentId);

    financialPeriodGuard.assertPostingAllowed(tenantId, payment.getPaymentDate());
    requireNotBoundToInstrument(tenantId, payment);
    if (paymentBindings.hasActiveMatches(tenantId, payment.getId()))
      throw PaymentReversalException.conflict(
          "PAYMENT_VOID_HAS_MATCHES",
          "This payment is matched to collection expectations; reverse it instead so the matches"
              + " are released on a dated posting");
    requireNoRefundHeld(tenantId, payment);
    payment.voidPayment();
    // A payment that paid a refund no longer does: the customer is owed that money again.
    paymentBindings.reopenRefunds(
        tenantId,
        payment.getId(),
        payment.getPaymentDate(),
        actingUser(),
        "Settling payment voided");

    paymentRepository.save(payment);

    eventPublisher.publish(
        new PaymentVoidedEvent(tenantId, payment.getId(), payment.getPaymentNumber(), List.of()));

    log.info("Voided payment {}", paymentId);
    return paymentMapper.toDto(payment);
  }

  /**
   * Reverses a whole payment of any method by posting an opposite-direction payment on {@code
   * request.reversalDate()}. The original stays as recorded, so closed periods keep their numbers
   * and the pair nets to zero. Invoice allocations and collection matches are released as of the
   * same date. A payment a cheque clearing relies on is refused here: the cheque's own flow
   * reverses it so the instrument keeps its evidence.
   */
  public PaymentDto reverse(UUID paymentId, ReversePaymentRequest request, UUID actor) {
    return doReverse(paymentId, request, actor, false);
  }

  /**
   * The reversal a cheque's own flow posts once it has released the instrument, so a dishonoured or
   * returned clearing is cancelled on a dated posting instead of being voided in place.
   */
  public PaymentDto reverseReleasedInstrument(
      UUID paymentId, ReversePaymentRequest request, UUID actor) {
    return doReverse(paymentId, request, actor, true);
  }

  private PaymentDto doReverse(
      UUID paymentId, ReversePaymentRequest request, UUID actor, boolean instrumentReleased) {
    if (actor == null) {
      throw new IllegalArgumentException("The acting user is required to reverse a payment");
    }
    UUID tenantId = TenantContext.requireTenantId();
    paymentRepository.lockFinanceTenant(tenantId);
    Payment original = getPaymentOrThrow(tenantId, paymentId);

    postingDates.assertNotFuture(request.reversalDate());
    financialPeriodGuard.assertPostingAllowed(tenantId, request.reversalDate());
    if (!instrumentReleased) {
      requireNotBoundToInstrument(tenantId, original);
      requireRefundsSettled(tenantId, original, request.acknowledgedPaidRefund());
    }
    // Before the first number or allocation is touched: a date earlier than a match it must undo
    // or than the history of a refund it would reopen or cancel.
    paymentBindings.assertMatchesReleasableOn(tenantId, original.getId(), request.reversalDate());
    paymentBindings.assertRefundsReleasableOn(tenantId, original.getId(), request.reversalDate());
    String reversalNumber =
        documentNumberGenerator.nextNumber(tenantId, "PAY", request.reversalDate().getYear());
    Payment.Reversal posted =
        original.reverse(reversalNumber, request.reversalDate(), request.reason());

    for (PaymentAllocation allocation : posted.releasedAllocations()) {
      invoicePaymentPort.reverseAllocation(
          tenantId, allocation.getInvoiceId(), allocation.getAmount());
      invoicePaymentPort.reverseAllocationFx(tenantId, allocation.getId());
    }
    List<UUID> invoiceIds =
        posted.releasedAllocations().stream().map(PaymentAllocation::getInvoiceId).toList();
    int matches =
        paymentBindings.releaseMatches(
            tenantId, original.getId(), request.reversalDate(), actor, request.reason());
    // The cheque did not honour, so what was set aside to give back never arrived. A refund that
    // was already paid stays: that money did leave.
    if (instrumentReleased)
      paymentBindings.cancelUnpaidRefunds(
          tenantId, original.getId(), request.reversalDate(), actor, request.reason());
    // A payment that paid a refund no longer does: the customer is owed that money again.
    paymentBindings.reopenRefunds(
        tenantId, original.getId(), request.reversalDate(), actor, request.reason());

    Payment saved = paymentRepository.saveAndFlush(posted.payment());
    original.markReversedBy(saved.getId());
    paymentRepository.saveAndFlush(original);

    eventPublisher.publish(
        new PaymentReversedEvent(
            tenantId,
            original.getId(),
            original.getPaymentNumber(),
            saved.getId(),
            saved.getPaymentNumber(),
            original.getTradingPartnerId(),
            original.getDirection(),
            original.getAmount(),
            saved.getPaymentDate(),
            invoiceIds));

    log.info(
        "Reversed payment {} with {} on {} (released {} allocations, {} matches)",
        original.getPaymentNumber(),
        saved.getPaymentNumber(),
        saved.getPaymentDate(),
        posted.releasedAllocations().size(),
        matches);
    return paymentMapper.toDto(saved);
  }

  @Transactional(readOnly = true)
  public PaymentDto getPayment(UUID paymentId) {
    return paymentMapper.toDto(getPaymentOrThrow(TenantContext.requireTenantId(), paymentId));
  }

  @Transactional(readOnly = true)
  public Page<PaymentDto> getPayments(Pageable pageable) {
    return paymentRepository
        .findByTenantId(TenantContext.requireTenantId(), pageable)
        .map(paymentMapper::toDto);
  }

  @Transactional(readOnly = true)
  public Page<PaymentDto> getPaymentsByPartner(UUID partnerId, Pageable pageable) {
    return paymentRepository
        .findByTenantIdAndTradingPartnerId(TenantContext.requireTenantId(), partnerId, pageable)
        .map(paymentMapper::toDto);
  }

  /** Only a cheque's own flow may undo the payment its clearing relies on. */
  private void requireNotBoundToInstrument(UUID tenantId, Payment payment) {
    if (paymentBindings.isBoundToInstrument(tenantId, payment.getId()))
      throw PaymentReversalException.conflict(
          "PAYMENT_BOUND_TO_INSTRUMENT",
          "This payment belongs to a cheque clearing; reverse it from the cheque");
  }

  /**
   * A refund nobody has paid is still owed, so it is cancelled before the receipt is taken back. A
   * refund already paid cannot be cancelled and its money left; the receipt may be reversed, but
   * only once the person has confirmed the amount that stays recorded and so is owed by the
   * customer. Asking again with the current amount covers a refund paid after the screen was read.
   */
  private void requireRefundsSettled(UUID tenantId, Payment payment, BigDecimal acknowledged) {
    if (!paymentBindings.holdsRefund(tenantId, payment.getId())) return;
    BigDecimal paid = paymentBindings.refundPaid(tenantId, payment.getId());
    BigDecimal unpaid = paymentBindings.refundHeld(tenantId, payment.getId()).subtract(paid);
    if (unpaid.signum() > 0)
      throw PaymentHeldForRefundException.cannotUndo(atCurrencyScale(unpaid, payment));
    if (acknowledged == null || acknowledged.compareTo(paid) != 0)
      throw PaymentHeldForRefundException.keepsPaidRefund(atCurrencyScale(paid, payment));
  }

  private void requireNoRefundHeld(UUID tenantId, Payment payment) {
    if (paymentBindings.holdsRefund(tenantId, payment.getId()))
      throw PaymentHeldForRefundException.cannotUndo(
          atCurrencyScale(paymentBindings.refundHeld(tenantId, payment.getId()), payment));
  }

  /** Only the part of a payment that is not set aside for a refund can be allocated. */
  private void requireNotHeldForRefund(UUID tenantId, Payment payment, Money allocationAmount) {
    if (!paymentBindings.holdsRefund(tenantId, payment.getId())) return;
    BigDecimal held = paymentBindings.refundHeld(tenantId, payment.getId());
    Money spendable =
        payment.getUnallocatedAmount().subtract(Money.of(held, payment.getCurrency()));
    if (allocationAmount.isGreaterThan(spendable))
      throw PaymentHeldForRefundException.cannotSpend(atCurrencyScale(held, payment));
  }

  /**
   * Ledger sums carry the column's scale (4); amounts shown to people, such as the paid refund they
   * must repeat, use the payment currency's own scale.
   */
  private static BigDecimal atCurrencyScale(BigDecimal amount, Payment payment) {
    return Money.of(amount, payment.getCurrency()).getAmount();
  }

  private static UUID actingUser() {
    UUID user = TenantContext.getCurrentUserId();
    return user != null ? user : TenantContext.SYSTEM_ACTOR_ID;
  }

  private Payment getPaymentOrThrow(UUID tenantId, UUID paymentId) {
    return paymentRepository
        .findByTenantIdAndId(tenantId, paymentId)
        .orElseThrow(() -> new FinanceDomainException("Payment not found: " + paymentId));
  }

  private PaymentDto withAllocations(
      PaymentDto paymentDto, List<PaymentAllocationDto> allocations) {
    return new PaymentDto(
        paymentDto.id(),
        paymentDto.tradingPartnerId(),
        paymentDto.paymentNumber(),
        paymentDto.direction(),
        paymentDto.method(),
        paymentDto.status(),
        paymentDto.amount(),
        paymentDto.currency(),
        paymentDto.allocatedAmount(),
        paymentDto.unallocatedAmount(),
        paymentDto.paymentDate(),
        paymentDto.bankReference(),
        paymentDto.notes(),
        paymentDto.metadata(),
        paymentDto.reversesPaymentId(),
        paymentDto.reversedByPaymentId(),
        paymentDto.reversalReason(),
        allocations,
        paymentDto.createdAt(),
        paymentDto.updatedAt());
  }
}
