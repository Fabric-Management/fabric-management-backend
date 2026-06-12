package com.fabricmanagement.finance.invoice.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceLine;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import com.fabricmanagement.finance.invoice.domain.event.*;
import com.fabricmanagement.finance.invoice.dto.*;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import com.fabricmanagement.finance.invoice.mapper.InvoiceMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InvoiceService {

  private final InvoiceRepository invoiceRepository;
  private final InvoiceMapper invoiceMapper;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public Page<InvoiceDto> getAllInvoices(Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();
    return invoiceRepository.findByTenantId(tenantId, pageable).map(invoiceMapper::toDto);
  }

  @Transactional(readOnly = true)
  public InvoiceDto getInvoice(UUID invoiceId) {
    Invoice invoice = getInvoiceOrThrow(TenantContext.requireTenantId(), invoiceId);
    return invoiceMapper.toDto(invoice);
  }

  public InvoiceDto createInvoice(CreateInvoiceRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    String invoiceNumber = generateInvoiceNumber(request.invoiceType());

    Invoice invoice =
        Invoice.builder()
            .tradingPartnerId(request.tradingPartnerId())
            .invoiceNumber(invoiceNumber)
            .orderReference(request.orderReference())
            .externalReference(request.externalReference())
            .invoiceType(InvoiceType.valueOf(request.invoiceType()))
            .issueDate(request.issueDate())
            .dueDate(request.dueDate())
            .subtotal(
                Money.of(
                    request.subtotal(), request.currency() != null ? request.currency() : "TRY"))
            .taxAmount(
                Money.of(
                    request.taxAmount() != null ? request.taxAmount() : BigDecimal.ZERO,
                    request.currency() != null ? request.currency() : "TRY"))
            .discountAmount(
                Money.of(
                    request.discountAmount() != null ? request.discountAmount() : BigDecimal.ZERO,
                    request.currency() != null ? request.currency() : "TRY"))
            .totalAmount(
                Money.of(
                    request.totalAmount(), request.currency() != null ? request.currency() : "TRY"))
            .taxRate(request.taxRate())
            .billingAddress(request.billingAddress())
            .notes(request.notes())
            .originalInvoiceId(request.originalInvoiceId())
            .build();

    invoice.setTenantId(tenantId);

    if (request.lines() != null && !request.lines().isEmpty()) {
      for (CreateInvoiceLineRequest lineReq : request.lines()) {
        InvoiceLine line =
            InvoiceLine.builder()
                .description(lineReq.description())
                .productCode(lineReq.productCode())
                .unit(lineReq.unit() != null ? lineReq.unit() : "PCS")
                .quantity(lineReq.quantity())
                .unitPrice(lineReq.unitPrice())
                .discountRate(
                    lineReq.discountRate() != null ? lineReq.discountRate() : BigDecimal.ZERO)
                .taxRate(lineReq.taxRate() != null ? lineReq.taxRate() : BigDecimal.ZERO)
                .notes(lineReq.notes())
                .build();
        line.setTenantId(tenantId);
        invoice.addLine(line);
      }
      invoice.recalculateFromLines();
      invoice.validateClientAmounts(request.subtotal(), request.totalAmount());
    } else {
      invoice.calculateAmounts();
    }

    Invoice saved = invoiceRepository.save(invoice);

    eventPublisher.publishEvent(
        new InvoiceCreatedEvent(
            tenantId,
            saved.getId(),
            saved.getTradingPartnerId(),
            saved.getInvoiceNumber(),
            saved.getInvoiceType().name(),
            saved.getTotalAmount().getAmount(),
            saved.getCurrency()));

    log.info("Invoice created: {} ({})", saved.getInvoiceNumber(), saved.getId());
    return invoiceMapper.toDto(saved);
  }

  public InvoiceDto updateInvoice(UUID invoiceId, UpdateInvoiceRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    Invoice invoice = getInvoiceOrThrow(tenantId, invoiceId);

    if (invoice.getStatus() != InvoiceStatus.DRAFT) {
      throw new FinanceDomainException("Can only update invoices in DRAFT status");
    }

    if (invoice.hasLines()) {
      if (request.subtotal() != null
          || request.taxAmount() != null
          || request.discountAmount() != null
          || request.totalAmount() != null) {
        throw new FinanceDomainException(
            "Cannot directly update monetary amounts on an invoice with lines; "
                + "modify the lines instead");
      }
    }

    if (request.orderReference() != null) invoice.setOrderReference(request.orderReference());
    if (request.externalReference() != null)
      invoice.setExternalReference(request.externalReference());
    if (request.issueDate() != null) invoice.setIssueDate(request.issueDate());
    if (request.dueDate() != null) invoice.setDueDate(request.dueDate());
    String curr = invoice.getCurrency();
    if (request.subtotal() != null) invoice.setSubtotal(Money.of(request.subtotal(), curr));
    if (request.taxAmount() != null) invoice.setTaxAmount(Money.of(request.taxAmount(), curr));
    if (request.discountAmount() != null)
      invoice.setDiscountAmount(Money.of(request.discountAmount(), curr));
    if (request.totalAmount() != null)
      invoice.setTotalAmount(Money.of(request.totalAmount(), curr));
    if (request.taxRate() != null) invoice.setTaxRate(request.taxRate());
    if (request.billingAddress() != null) invoice.setBillingAddress(request.billingAddress());
    if (request.notes() != null) invoice.setNotes(request.notes());

    if (invoice.hasLines()) {
      invoice.recalculateFromLines();
    } else {
      invoice.calculateAmounts();
    }
    Invoice saved = invoiceRepository.save(invoice);
    return invoiceMapper.toDto(saved);
  }

  public InvoiceDto issueInvoice(UUID invoiceId) {
    UUID tenantId = TenantContext.requireTenantId();
    Invoice invoice = getInvoiceOrThrow(tenantId, invoiceId);
    invoice.issue();
    Invoice saved = invoiceRepository.save(invoice);

    eventPublisher.publishEvent(
        new InvoiceIssuedEvent(
            tenantId, saved.getId(), saved.getInvoiceNumber(), saved.getTotalAmount().getAmount()));

    log.info("Invoice issued: {}", saved.getInvoiceNumber());
    return invoiceMapper.toDto(saved);
  }

  public InvoiceDto sendInvoice(UUID invoiceId) {
    UUID tenantId = TenantContext.requireTenantId();
    Invoice invoice = getInvoiceOrThrow(tenantId, invoiceId);
    invoice.send();
    Invoice saved = invoiceRepository.save(invoice);

    eventPublisher.publishEvent(
        new InvoiceSentEvent(
            tenantId, saved.getId(), saved.getInvoiceNumber(), saved.getTradingPartnerId()));

    log.info("Invoice sent: {}", saved.getInvoiceNumber());
    return invoiceMapper.toDto(saved);
  }

  public InvoiceDto recordPayment(UUID invoiceId, BigDecimal amount) {
    UUID tenantId = TenantContext.requireTenantId();
    Invoice invoice = getInvoiceOrThrow(tenantId, invoiceId);
    invoice.recordPayment(Money.of(amount, invoice.getCurrency()));
    Invoice saved = invoiceRepository.save(invoice);

    eventPublisher.publishEvent(
        new PaymentRecordedEvent(
            tenantId,
            saved.getId(),
            saved.getInvoiceNumber(),
            amount,
            saved.getAmountPaid().getAmount(),
            saved.getAmountDue().getAmount(),
            saved.getStatus() == InvoiceStatus.PAID));

    log.info(
        "Payment recorded for invoice {}: {} (remaining: {})",
        saved.getInvoiceNumber(),
        amount,
        saved.getAmountDue());
    return invoiceMapper.toDto(saved);
  }

  public InvoiceDto cancelInvoice(UUID invoiceId) {
    UUID tenantId = TenantContext.requireTenantId();
    Invoice invoice = getInvoiceOrThrow(tenantId, invoiceId);
    invoice.cancel();
    Invoice saved = invoiceRepository.save(invoice);

    eventPublisher.publishEvent(
        new InvoiceCancelledEvent(tenantId, saved.getId(), saved.getInvoiceNumber()));

    log.info("Invoice cancelled: {}", saved.getInvoiceNumber());
    return invoiceMapper.toDto(saved);
  }

  public InvoiceDto voidInvoice(UUID invoiceId) {
    UUID tenantId = TenantContext.requireTenantId();
    Invoice invoice = getInvoiceOrThrow(tenantId, invoiceId);
    invoice.voidInvoice();
    Invoice saved = invoiceRepository.save(invoice);
    log.info("Invoice voided: {}", saved.getInvoiceNumber());
    return invoiceMapper.toDto(saved);
  }

  public InvoiceDto disputeInvoice(UUID invoiceId) {
    UUID tenantId = TenantContext.requireTenantId();
    Invoice invoice = getInvoiceOrThrow(tenantId, invoiceId);
    invoice.dispute();
    Invoice saved = invoiceRepository.save(invoice);

    eventPublisher.publishEvent(
        new InvoiceDisputedEvent(
            tenantId, saved.getId(), saved.getInvoiceNumber(), saved.getTradingPartnerId()));

    log.info("Invoice disputed: {}", saved.getInvoiceNumber());
    return invoiceMapper.toDto(saved);
  }

  public InvoiceDto resolveDispute(UUID invoiceId) {
    UUID tenantId = TenantContext.requireTenantId();
    Invoice invoice = getInvoiceOrThrow(tenantId, invoiceId);
    invoice.resolveDispute();
    Invoice saved = invoiceRepository.save(invoice);
    log.info("Invoice dispute resolved: {}", saved.getInvoiceNumber());
    return invoiceMapper.toDto(saved);
  }

  public void deleteInvoice(UUID invoiceId) {
    UUID tenantId = TenantContext.requireTenantId();
    Invoice invoice = getInvoiceOrThrow(tenantId, invoiceId);
    if (invoice.getStatus() != InvoiceStatus.DRAFT) {
      throw new FinanceDomainException("Can only delete invoices in DRAFT status");
    }
    invoice.delete();
    invoiceRepository.save(invoice);
    log.info("Invoice soft-deleted: {}", invoice.getInvoiceNumber());
  }

  @Transactional(readOnly = true)
  public Page<InvoiceDto> getByPartner(UUID partnerId, Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();
    return invoiceRepository
        .findByTenantIdAndTradingPartnerId(tenantId, partnerId, pageable)
        .map(invoiceMapper::toDto);
  }

  @Transactional(readOnly = true)
  public Page<InvoiceDto> getUnpaidByPartner(UUID partnerId, Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();
    return invoiceRepository
        .findByTenantIdAndTradingPartnerIdAndStatusNot(
            tenantId, partnerId, InvoiceStatus.PAID, pageable)
        .map(invoiceMapper::toDto);
  }

  @Transactional(readOnly = true)
  public Page<InvoiceDto> getByStatus(InvoiceStatus status, Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();
    return invoiceRepository
        .findByTenantIdAndStatus(tenantId, status, pageable)
        .map(invoiceMapper::toDto);
  }

  @Transactional(readOnly = true)
  public Page<InvoiceDto> getOverdue(Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();
    return invoiceRepository
        .findOverdue(tenantId, LocalDate.now(), pageable)
        .map(invoiceMapper::toDto);
  }

  @Transactional(readOnly = true)
  public Page<InvoiceDto> getAwaitingPayment(Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();
    return invoiceRepository.findAwaitingPayment(tenantId, pageable).map(invoiceMapper::toDto);
  }

  @Transactional(readOnly = true)
  public Page<InvoiceDto> getAccountsReceivable(Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();
    return invoiceRepository.findAccountsReceivable(tenantId, pageable).map(invoiceMapper::toDto);
  }

  @Transactional(readOnly = true)
  public Page<InvoiceDto> getAccountsPayable(Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();
    return invoiceRepository.findAccountsPayable(tenantId, pageable).map(invoiceMapper::toDto);
  }

  public int markOverdueInvoices(UUID tenantId) {
    List<Invoice> eligibles =
        invoiceRepository.findInvoicesEligibleForOverdue(tenantId, LocalDate.now());
    int count = 0;
    for (Invoice invoice : eligibles) {
      invoice.markOverdue();
      invoiceRepository.save(invoice);

      eventPublisher.publishEvent(
          new InvoiceOverdueEvent(
              tenantId,
              invoice.getId(),
              invoice.getInvoiceNumber(),
              invoice.getTradingPartnerId(),
              invoice.getDaysOverdue()));
      count++;
    }
    if (count > 0) {
      log.info("Marked {} invoices as overdue for tenant {}", count, tenantId);
    }
    return count;
  }

  private String generateInvoiceNumber(String invoiceType) {
    String prefix;
    switch (invoiceType) {
      case "PURCHASE" -> prefix = "PF";
      case "CREDIT_NOTE" -> prefix = "CN";
      case "DEBIT_NOTE" -> prefix = "DN";
      case "PROFORMA" -> prefix = "PQ";
      default -> prefix = "SF";
    }

    Long seq = invoiceRepository.getNextInvoiceSequence();
    String number = String.format("%s-%06d", prefix, seq);

    int attempt = 0;
    while (invoiceRepository.existsByTenantIdAndInvoiceNumber(
            TenantContext.requireTenantId(), number)
        && attempt < 10) {
      seq = invoiceRepository.getNextInvoiceSequence();
      number = String.format("%s-%06d", prefix, seq);
      attempt++;
    }

    return number;
  }

  private Invoice getInvoiceOrThrow(UUID tenantId, UUID invoiceId) {
    return invoiceRepository
        .findByTenantIdAndId(tenantId, invoiceId)
        .orElseThrow(() -> new NotFoundException("Invoice not found: " + invoiceId));
  }
}
