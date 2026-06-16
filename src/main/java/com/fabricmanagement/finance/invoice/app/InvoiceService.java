package com.fabricmanagement.finance.invoice.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.common.app.FinanceDocumentNumberGenerator;
import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceLine;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import com.fabricmanagement.finance.invoice.domain.event.*;
import com.fabricmanagement.finance.invoice.dto.*;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import com.fabricmanagement.finance.invoice.mapper.InvoiceMapper;
import com.fabricmanagement.finance.period.app.port.FinancialPeriodGuard;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
  private final DomainEventPublisher eventPublisher;
  private final TenantReportingCurrencyPort reportingCurrencyPort;
  private final FinanceDocumentNumberGenerator documentNumberGenerator;
  private final InvoiceReportingSnapshotService reportingSnapshotService;
  private final FinancialPeriodGuard financialPeriodGuard;

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
    InvoiceType type = InvoiceType.valueOf(request.invoiceType());
    if (type == InvoiceType.CREDIT_NOTE) {
      if (request.originalInvoiceId() == null) {
        throw new FinanceDomainException("originalInvoiceId is required for CREDIT_NOTE");
      }
      Invoice original = getInvoiceOrThrow(tenantId, request.originalInvoiceId());
      if (!original.getTradingPartnerId().equals(request.tradingPartnerId())) {
        throw new FinanceDomainException(
            "CREDIT_NOTE tradingPartnerId must match original invoice");
      }
      if (!original.getInvoiceType().isSettleable()) {
        throw new FinanceDomainException(
            "Original invoice type is not settleable (e.g. cannot be PROFORMA)");
      }
    }

    String prefix = getPrefixForType(type);
    String invoiceNumber =
        documentNumberGenerator.nextNumber(tenantId, prefix, request.issueDate().getYear());

    String resolvedCurrency =
        request.currency() != null
            ? request.currency()
            : reportingCurrencyPort.getReportingCurrency(tenantId);

    Invoice invoice =
        Invoice.builder()
            .tradingPartnerId(request.tradingPartnerId())
            .invoiceNumber(invoiceNumber)
            .orderReference(request.orderReference())
            .externalReference(request.externalReference())
            .invoiceType(type)
            .issueDate(request.issueDate())
            .dueDate(request.dueDate())
            .subtotal(Money.of(request.subtotal(), resolvedCurrency))
            .taxAmount(
                Money.of(
                    request.taxAmount() != null ? request.taxAmount() : BigDecimal.ZERO,
                    resolvedCurrency))
            .discountAmount(
                Money.of(
                    request.discountAmount() != null ? request.discountAmount() : BigDecimal.ZERO,
                    resolvedCurrency))
            .totalAmount(Money.of(request.totalAmount(), resolvedCurrency))
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
                .taxCategory(parseTaxCategory(lineReq.taxCategory()))
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

    eventPublisher.publish(
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
    financialPeriodGuard.assertPostingAllowed(tenantId, invoice.getIssueDate());
    invoice.issue();
    reportingSnapshotService.captureAtIssue(tenantId, invoice);
    Invoice saved = invoiceRepository.save(invoice);

    eventPublisher.publish(
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

    eventPublisher.publish(
        new InvoiceSentEvent(
            tenantId, saved.getId(), saved.getInvoiceNumber(), saved.getTradingPartnerId()));

    log.info("Invoice sent: {}", saved.getInvoiceNumber());
    return invoiceMapper.toDto(saved);
  }

  public InvoiceDto cancelInvoice(UUID invoiceId) {
    UUID tenantId = TenantContext.requireTenantId();
    Invoice invoice = getInvoiceOrThrow(tenantId, invoiceId);
    invoice.cancel();
    Invoice saved = invoiceRepository.save(invoice);

    eventPublisher.publish(
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

    eventPublisher.publish(
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
        .findByTenantIdAndTradingPartnerIdAndPaymentStatusNot(
            tenantId,
            partnerId,
            com.fabricmanagement.finance.invoice.domain.InvoicePaymentStatus.PAID,
            pageable)
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
    return invoiceRepository
        .findAccountsReceivable(
            TenantContext.requireTenantId(),
            List.of(InvoiceType.SALES, InvoiceType.DEBIT_NOTE),
            InvoiceType.CREDIT_NOTE,
            List.of(InvoiceStatus.CANCELLED, InvoiceStatus.VOIDED, InvoiceStatus.DRAFT),
            pageable)
        .map(invoiceMapper::toDto);
  }

  @Transactional(readOnly = true)
  public Page<InvoiceDto> getAccountsPayable(Pageable pageable) {
    return invoiceRepository
        .findAccountsPayable(
            TenantContext.requireTenantId(),
            InvoiceType.PURCHASE,
            InvoiceType.CREDIT_NOTE,
            List.of(InvoiceStatus.CANCELLED, InvoiceStatus.VOIDED, InvoiceStatus.DRAFT),
            pageable)
        .map(invoiceMapper::toDto);
  }

  public int notifyOverdueInvoices(UUID tenantId) {
    List<Invoice> eligibles =
        invoiceRepository.findInvoicesEligibleForOverdue(tenantId, LocalDate.now());
    int count = 0;
    for (Invoice invoice : eligibles) {

      eventPublisher.publish(
          new InvoiceOverdueEvent(
              tenantId,
              invoice.getId(),
              invoice.getInvoiceNumber(),
              invoice.getTradingPartnerId(),
              invoice.getDaysOverdue()));
      count++;
    }
    if (count > 0) {
      log.info("Sent overdue notifications for {} invoices for tenant {}", count, tenantId);
    }
    return count;
  }

  private String getPrefixForType(InvoiceType type) {
    return switch (type) {
      case SALES -> "SF";
      case PURCHASE -> "PF";
      case CREDIT_NOTE -> "CN";
      case DEBIT_NOTE -> "DN";
      case PROFORMA -> "PQ";
    };
  }

  private Invoice getInvoiceOrThrow(UUID tenantId, UUID invoiceId) {
    return invoiceRepository
        .findByTenantIdAndId(tenantId, invoiceId)
        .orElseThrow(() -> new NotFoundException("Invoice not found: " + invoiceId));
  }

  private com.fabricmanagement.finance.invoice.domain.TaxCategory parseTaxCategory(String value) {
    if (value == null) return com.fabricmanagement.finance.invoice.domain.TaxCategory.STANDARD;
    try {
      return com.fabricmanagement.finance.invoice.domain.TaxCategory.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new FinanceDomainException(
          "Invalid tax category: "
              + value
              + ". Valid values: "
              + java.util.Arrays.toString(
                  com.fabricmanagement.finance.invoice.domain.TaxCategory.values()));
    }
  }
}
