package com.fabricmanagement.finance.payment.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.common.app.FinanceDocumentNumberGenerator;
import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import com.fabricmanagement.finance.payment.app.port.InvoiceAllocationView;
import com.fabricmanagement.finance.payment.app.port.InvoicePaymentPort;
import com.fabricmanagement.finance.payment.domain.Payment;
import com.fabricmanagement.finance.payment.domain.PaymentAllocation;
import com.fabricmanagement.finance.payment.domain.PaymentDirection;
import com.fabricmanagement.finance.payment.domain.PaymentMethod;
import com.fabricmanagement.finance.payment.domain.event.PaymentAllocatedEvent;
import com.fabricmanagement.finance.payment.domain.event.PaymentReceivedEvent;
import com.fabricmanagement.finance.payment.domain.event.PaymentVoidedEvent;
import com.fabricmanagement.finance.payment.dto.CreateAllocationRequest;
import com.fabricmanagement.finance.payment.dto.CreatePaymentRequest;
import com.fabricmanagement.finance.payment.dto.PaymentAllocationDto;
import com.fabricmanagement.finance.payment.dto.PaymentDto;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import com.fabricmanagement.finance.payment.mapper.PaymentMapper;
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

  public PaymentDto createPayment(CreatePaymentRequest request) {
    UUID tenantId = TenantContext.requireTenantId();

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

    if (request.allocations() != null && !request.allocations().isEmpty()) {
      for (CreateAllocationRequest allocRequest : request.allocations()) {
        doAllocate(tenantId, saved, allocRequest);
      }
    }

    return paymentMapper.toDto(saved);
  }

  public PaymentAllocationDto allocatePayment(UUID paymentId, CreateAllocationRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    Payment payment = getPaymentOrThrow(tenantId, paymentId);
    return doAllocate(tenantId, payment, request);
  }

  private PaymentAllocationDto doAllocate(
      UUID tenantId, Payment payment, CreateAllocationRequest request) {
    InvoiceAllocationView invoiceView =
        invoicePaymentPort.getInvoiceForAllocation(tenantId, request.invoiceId());

    if (!invoiceView.payable()) {
      throw new FinanceDomainException("Invoice is not in a payable state");
    }

    Money allocationAmount = Money.of(request.amount(), invoiceView.currency());

    PaymentAllocation allocation =
        payment.allocate(
            invoiceView.invoiceId(),
            allocationAmount,
            invoiceView.openBalance(),
            invoiceView.currency());

    paymentRepository.save(payment);

    invoicePaymentPort.applyAllocation(
        tenantId, invoiceView.invoiceId(), allocation.getAmount(), payment.getPaymentDate());

    eventPublisher.publish(
        new PaymentAllocatedEvent(
            tenantId, payment.getId(), invoiceView.invoiceId(), allocationAmount));

    log.info(
        "Allocated {} from payment {} to invoice {}",
        allocationAmount,
        payment.getId(),
        request.invoiceId());
    return paymentMapper.toAllocationDto(allocation);
  }

  public void deallocatePayment(UUID paymentId, UUID allocationId) {
    UUID tenantId = TenantContext.requireTenantId();
    Payment payment = getPaymentOrThrow(tenantId, paymentId);

    PaymentAllocation allocation = payment.deallocate(allocationId);

    invoicePaymentPort.reverseAllocation(
        tenantId, allocation.getInvoiceId(), allocation.getAmount());

    paymentRepository.save(payment);

    log.info(
        "Deallocated {} from payment {} (allocation: {})",
        allocation.getAmount(),
        paymentId,
        allocationId);
  }

  public PaymentDto voidPayment(UUID paymentId) {
    UUID tenantId = TenantContext.requireTenantId();
    Payment payment = getPaymentOrThrow(tenantId, paymentId);

    List<PaymentAllocation> reversed = payment.voidPayment();

    List<UUID> affectedInvoiceIds = reversed.stream().map(PaymentAllocation::getInvoiceId).toList();

    for (PaymentAllocation allocation : reversed) {
      invoicePaymentPort.reverseAllocation(
          tenantId, allocation.getInvoiceId(), allocation.getAmount());
    }

    paymentRepository.save(payment);

    eventPublisher.publish(
        new PaymentVoidedEvent(
            tenantId, payment.getId(), payment.getPaymentNumber(), affectedInvoiceIds));

    log.info("Voided payment {} and reversed {} allocations", paymentId, reversed.size());
    return paymentMapper.toDto(payment);
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

  private Payment getPaymentOrThrow(UUID tenantId, UUID paymentId) {
    return paymentRepository
        .findByTenantIdAndId(tenantId, paymentId)
        .orElseThrow(() -> new FinanceDomainException("Payment not found: " + paymentId));
  }
}
