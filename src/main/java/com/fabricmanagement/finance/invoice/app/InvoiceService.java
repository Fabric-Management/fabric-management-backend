package com.fabricmanagement.finance.invoice.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.company.app.TradingPartnerResolver;
import com.fabricmanagement.common.platform.company.app.TradingPartnerService;
import com.fabricmanagement.common.platform.company.dto.TradingPartnerDto;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import com.fabricmanagement.finance.invoice.dto.CreateInvoiceRequest;
import com.fabricmanagement.finance.invoice.dto.InvoiceDto;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing invoices.
 *
 * <p>Uses TradingPartnerResolver for partner ID resolution (Faz 1.5 pattern). Supports both AR
 * (Accounts Receivable) and AP (Accounts Payable) invoices.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

  private final InvoiceRepository invoiceRepository;
  private final TradingPartnerResolver partnerResolver;
  private final TradingPartnerService partnerService;

  private static final DateTimeFormatter INVOICE_NUMBER_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd");

  // ═══════════════════════════════════════════════════════════════════════════
  // CREATION
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Create a new invoice.
   *
   * @param request Invoice creation request
   * @return Created invoice DTO
   */
  @Transactional
  public InvoiceDto createInvoice(CreateInvoiceRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    // Resolve partner ID (handles both new and legacy IDs)
    UUID tradingPartnerId = partnerResolver.resolvePartnerId(tenantId, request.getPartnerId());

    // Generate invoice number
    String invoiceNumber =
        generateInvoiceNumber(tenantId, request.getIssueDate(), request.getInvoiceType());

    Invoice invoice =
        Invoice.builder()
            .tradingPartnerId(tradingPartnerId)
            .invoiceNumber(invoiceNumber)
            .orderReference(request.getOrderReference())
            .externalReference(request.getExternalReference())
            .invoiceType(request.getInvoiceType())
            .issueDate(request.getIssueDate())
            .dueDate(request.getDueDate())
            .subtotal(request.getSubtotal())
            .taxAmount(request.getTaxAmount())
            .discountAmount(request.getDiscountAmount())
            .currency(request.getCurrency())
            .taxRate(request.getTaxRate())
            .billingAddress(request.getBillingAddress())
            .notes(request.getNotes())
            .metadata(request.getMetadata())
            .build();

    // Calculate amounts
    invoice.calculateAmounts();

    Invoice saved = invoiceRepository.save(invoice);

    // Get partner details for response
    TradingPartnerDto partner = partnerService.findById(tenantId, tradingPartnerId).orElse(null);

    log.info(
        "Invoice created: uid={}, partner={}, type={}",
        saved.getUid(),
        tradingPartnerId,
        saved.getInvoiceType());
    return InvoiceDto.from(saved, partner);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // QUERIES
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Find invoice by ID.
   *
   * @param invoiceId Invoice ID
   * @return Invoice DTO if found
   */
  @Transactional(readOnly = true)
  public Optional<InvoiceDto> findById(UUID invoiceId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    return invoiceRepository
        .findByTenantIdAndId(tenantId, invoiceId)
        .map(
            invoice -> {
              TradingPartnerDto partner =
                  partnerService.findById(tenantId, invoice.getTradingPartnerId()).orElse(null);
              return InvoiceDto.from(invoice, partner);
            });
  }

  /**
   * Find invoice by invoice number.
   *
   * @param invoiceNumber Invoice number
   * @return Invoice DTO if found
   */
  @Transactional(readOnly = true)
  public Optional<InvoiceDto> findByInvoiceNumber(String invoiceNumber) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    return invoiceRepository
        .findByTenantIdAndInvoiceNumber(tenantId, invoiceNumber)
        .map(InvoiceDto::from);
  }

  /**
   * Find invoices by partner ID.
   *
   * @param partnerId Partner ID (can be TradingPartner.id or legacy Company.id)
   * @return List of invoices
   */
  @Transactional(readOnly = true)
  public List<InvoiceDto> findByPartner(UUID partnerId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    // Resolve partner ID
    UUID tradingPartnerId = partnerResolver.resolvePartnerId(tenantId, partnerId);

    return invoiceRepository.findActiveByPartner(tenantId, tradingPartnerId).stream()
        .map(InvoiceDto::from)
        .toList();
  }

  /**
   * Find unpaid invoices by partner.
   *
   * @param partnerId Partner ID
   * @return List of unpaid invoices
   */
  @Transactional(readOnly = true)
  public List<InvoiceDto> findUnpaidByPartner(UUID partnerId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    UUID tradingPartnerId = partnerResolver.resolvePartnerId(tenantId, partnerId);

    return invoiceRepository.findUnpaidByPartner(tenantId, tradingPartnerId).stream()
        .map(InvoiceDto::from)
        .toList();
  }

  /**
   * Get outstanding amount for a partner.
   *
   * @param partnerId Partner ID
   * @return Total outstanding amount
   */
  @Transactional(readOnly = true)
  public BigDecimal getOutstandingAmount(UUID partnerId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    UUID tradingPartnerId = partnerResolver.resolvePartnerId(tenantId, partnerId);

    return invoiceRepository.sumOutstandingByPartner(tenantId, tradingPartnerId);
  }

  /**
   * Find invoices by status.
   *
   * @param status Invoice status
   * @return List of invoices
   */
  @Transactional(readOnly = true)
  public List<InvoiceDto> findByStatus(InvoiceStatus status) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    return invoiceRepository.findByTenantIdAndStatus(tenantId, status).stream()
        .map(InvoiceDto::from)
        .toList();
  }

  /**
   * Find overdue invoices.
   *
   * @return List of overdue invoices
   */
  @Transactional(readOnly = true)
  public List<InvoiceDto> findOverdueInvoices() {
    UUID tenantId = TenantContext.getCurrentTenantId();

    return invoiceRepository.findOverdueInvoices(tenantId, LocalDate.now()).stream()
        .map(InvoiceDto::from)
        .toList();
  }

  /**
   * Find invoices awaiting payment.
   *
   * @return List of invoices awaiting payment
   */
  @Transactional(readOnly = true)
  public List<InvoiceDto> findAwaitingPayment() {
    UUID tenantId = TenantContext.getCurrentTenantId();

    return invoiceRepository.findAwaitingPayment(tenantId).stream().map(InvoiceDto::from).toList();
  }

  /**
   * Find AR invoices (sales).
   *
   * @return List of AR invoices
   */
  @Transactional(readOnly = true)
  public List<InvoiceDto> findAccountsReceivable() {
    UUID tenantId = TenantContext.getCurrentTenantId();

    return invoiceRepository.findAccountsReceivable(tenantId).stream()
        .map(InvoiceDto::from)
        .toList();
  }

  /**
   * Find AP invoices (purchases).
   *
   * @return List of AP invoices
   */
  @Transactional(readOnly = true)
  public List<InvoiceDto> findAccountsPayable() {
    UUID tenantId = TenantContext.getCurrentTenantId();

    return invoiceRepository.findAccountsPayable(tenantId).stream().map(InvoiceDto::from).toList();
  }

  /**
   * Get all invoices with pagination.
   *
   * @param pageable Pagination info
   * @return Page of invoices
   */
  @Transactional(readOnly = true)
  public Page<InvoiceDto> findAll(Pageable pageable) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    return invoiceRepository
        .findByTenantIdAndIsActiveTrue(tenantId, pageable)
        .map(InvoiceDto::from);
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // LIFECYCLE
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Issue an invoice.
   *
   * @param invoiceId Invoice ID
   * @return Updated invoice DTO
   */
  @Transactional
  public InvoiceDto issueInvoice(UUID invoiceId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    Invoice invoice = getInvoiceOrThrow(tenantId, invoiceId);
    invoice.issue();
    Invoice saved = invoiceRepository.save(invoice);

    log.info("Invoice issued: uid={}", saved.getUid());
    return InvoiceDto.from(saved);
  }

  /**
   * Send an invoice.
   *
   * @param invoiceId Invoice ID
   * @return Updated invoice DTO
   */
  @Transactional
  public InvoiceDto sendInvoice(UUID invoiceId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    Invoice invoice = getInvoiceOrThrow(tenantId, invoiceId);
    invoice.send();
    Invoice saved = invoiceRepository.save(invoice);

    log.info("Invoice sent: uid={}", saved.getUid());
    return InvoiceDto.from(saved);
  }

  /**
   * Record a payment.
   *
   * @param invoiceId Invoice ID
   * @param amount Payment amount
   * @return Updated invoice DTO
   */
  @Transactional
  public InvoiceDto recordPayment(UUID invoiceId, BigDecimal amount) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    Invoice invoice = getInvoiceOrThrow(tenantId, invoiceId);
    invoice.recordPayment(amount);
    Invoice saved = invoiceRepository.save(invoice);

    log.info(
        "Payment recorded: uid={}, amount={}, newStatus={}",
        saved.getUid(),
        amount,
        saved.getStatus());
    return InvoiceDto.from(saved);
  }

  /**
   * Cancel an invoice.
   *
   * @param invoiceId Invoice ID
   * @return Updated invoice DTO
   */
  @Transactional
  public InvoiceDto cancelInvoice(UUID invoiceId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    Invoice invoice = getInvoiceOrThrow(tenantId, invoiceId);
    invoice.cancel();
    Invoice saved = invoiceRepository.save(invoice);

    log.info("Invoice cancelled: uid={}", saved.getUid());
    return InvoiceDto.from(saved);
  }

  /**
   * Void an invoice.
   *
   * @param invoiceId Invoice ID
   * @return Updated invoice DTO
   */
  @Transactional
  public InvoiceDto voidInvoice(UUID invoiceId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    Invoice invoice = getInvoiceOrThrow(tenantId, invoiceId);
    invoice.voidInvoice();
    Invoice saved = invoiceRepository.save(invoice);

    log.info("Invoice voided: uid={}", saved.getUid());
    return InvoiceDto.from(saved);
  }

  /**
   * Soft delete an invoice.
   *
   * @param invoiceId Invoice ID
   */
  @Transactional
  public void deleteInvoice(UUID invoiceId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    Invoice invoice = getInvoiceOrThrow(tenantId, invoiceId);
    invoice.delete();
    invoiceRepository.save(invoice);

    log.info("Invoice deleted (soft): uid={}", invoice.getUid());
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // BATCH OPERATIONS
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Mark overdue invoices (batch job).
   *
   * @return Number of invoices marked as overdue
   */
  @Transactional
  public int markOverdueInvoices() {
    UUID tenantId = TenantContext.getCurrentTenantId();

    List<Invoice> overdueInvoices =
        invoiceRepository.findOverdueInvoices(tenantId, LocalDate.now());

    int count = 0;
    for (Invoice invoice : overdueInvoices) {
      if (invoice.getStatus() != InvoiceStatus.OVERDUE) {
        invoice.markOverdue();
        invoiceRepository.save(invoice);
        count++;
      }
    }

    if (count > 0) {
      log.info("Marked {} invoices as overdue", count);
    }

    return count;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // HELPERS
  // ═══════════════════════════════════════════════════════════════════════════

  private Invoice getInvoiceOrThrow(UUID tenantId, UUID invoiceId) {
    return invoiceRepository
        .findByTenantIdAndId(tenantId, invoiceId)
        .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + invoiceId));
  }

  private String generateInvoiceNumber(UUID tenantId, LocalDate issueDate, InvoiceType type) {
    String prefix =
        (type == InvoiceType.SALES ? "INV" : "PINV")
            + "-"
            + issueDate.format(INVOICE_NUMBER_DATE_FORMAT)
            + "-";
    String maxNumber =
        invoiceRepository.findMaxInvoiceNumber(tenantId, prefix).orElse(prefix + "00000");

    // Extract sequence number and increment
    String sequencePart = maxNumber.substring(prefix.length());
    int sequence = Integer.parseInt(sequencePart) + 1;

    return prefix + String.format("%05d", sequence);
  }
}
