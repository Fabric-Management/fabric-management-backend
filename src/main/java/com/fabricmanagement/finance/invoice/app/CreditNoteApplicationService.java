package com.fabricmanagement.finance.invoice.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import com.fabricmanagement.finance.invoice.domain.CreditNoteApplication;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import com.fabricmanagement.finance.invoice.domain.event.CreditNoteAppliedEvent;
import com.fabricmanagement.finance.invoice.domain.event.CreditNoteReversedEvent;
import com.fabricmanagement.finance.invoice.dto.CreateCreditNoteApplicationRequest;
import com.fabricmanagement.finance.invoice.dto.CreditNoteApplicationDto;
import com.fabricmanagement.finance.invoice.infra.repository.CreditNoteApplicationRepository;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CreditNoteApplicationService {

  private final InvoiceRepository invoiceRepository;
  private final CreditNoteApplicationRepository applicationRepository;
  private final DomainEventPublisher eventPublisher;
  private final Clock clock;

  public CreditNoteApplicationDto applyCreditNote(
      UUID creditNoteId, CreateCreditNoteApplicationRequest request) {
    UUID tenantId = TenantContext.requireTenantId();

    Invoice creditNote = getInvoiceOrThrow(tenantId, creditNoteId);
    if (creditNote.getInvoiceType() != InvoiceType.CREDIT_NOTE) {
      throw new FinanceDomainException("Source document must be a CREDIT_NOTE");
    }
    if (!creditNote.getStatus().canReceivePayment()) { // Must be ISSUED/SENT (at least SENT)
      throw new FinanceDomainException("Credit note must be issued/sent to be applied");
    }

    Invoice targetInvoice = getInvoiceOrThrow(tenantId, request.targetInvoiceId());
    if (creditNote.getId().equals(targetInvoice.getId())) {
      throw new FinanceDomainException("Cannot apply credit note to itself");
    }
    if (!targetInvoice.getStatus().canReceivePayment()) {
      throw new FinanceDomainException("Target invoice must be in a payable state");
    }
    if (!targetInvoice.getInvoiceType().isSettleable()) {
      throw new FinanceDomainException("Target invoice type is not settleable");
    }
    if (!creditNote.getTradingPartnerId().equals(targetInvoice.getTradingPartnerId())) {
      throw new FinanceDomainException(
          "Trading partner mismatch between credit note and target invoice");
    }
    if (!creditNote.getCurrency().equals(targetInvoice.getCurrency())) {
      throw new FinanceDomainException("Currency mismatch between credit note and target invoice");
    }

    // Resolve CN side
    Invoice originalInvoice =
        invoiceRepository
            .findByTenantIdAndId(tenantId, creditNote.getOriginalInvoiceId())
            .orElseThrow(
                () -> new FinanceDomainException("Original invoice not found for credit note"));

    boolean isAr = originalInvoice.getInvoiceType().isReceivable();
    boolean isAp = originalInvoice.getInvoiceType().isPayable();
    boolean targetAr = targetInvoice.getInvoiceType().isReceivable();
    boolean targetAp = targetInvoice.getInvoiceType().isPayable();

    if ((isAr && !targetAr) || (isAp && !targetAp)) {
      throw new FinanceDomainException(
          "Cannot apply credit note to invoice on a different side (AR/AP mismatch)");
    }

    BigDecimal currentApplied =
        applicationRepository.sumAppliedAmount(tenantId, creditNote.getId());
    Money unappliedAmount =
        creditNote.getTotalAmount().subtract(Money.of(currentApplied, creditNote.getCurrency()));
    Money applicationAmount = Money.of(request.amount(), creditNote.getCurrency());

    if (applicationAmount.isGreaterThan(unappliedAmount)) {
      throw new FinanceDomainException("Application amount exceeds credit note unapplied balance");
    }
    if (applicationAmount.isGreaterThan(targetInvoice.getAmountDue())) {
      throw new FinanceDomainException("Application amount exceeds target invoice open balance");
    }

    targetInvoice.applyCredit(applicationAmount, LocalDate.now(clock));
    invoiceRepository.save(targetInvoice);

    creditNote.applyCredit(applicationAmount, LocalDate.now(clock));
    invoiceRepository.save(creditNote);

    CreditNoteApplication application =
        CreditNoteApplication.builder()
            .creditNoteId(creditNote.getId())
            .targetInvoiceId(targetInvoice.getId())
            .amount(applicationAmount)
            .appliedAt(Instant.now(clock))
            .build();
    application.setTenantId(tenantId);
    CreditNoteApplication saved = applicationRepository.save(application);

    eventPublisher.publish(
        new CreditNoteAppliedEvent(
            tenantId,
            creditNote.getId(),
            targetInvoice.getId(),
            applicationAmount,
            targetInvoice.getPaymentStatus().name()));

    log.info(
        "Applied credit note {} to invoice {} for {}",
        creditNote.getInvoiceNumber(),
        targetInvoice.getInvoiceNumber(),
        applicationAmount);

    return toDto(saved);
  }

  public void reverseCreditNoteApplication(UUID creditNoteId, UUID applicationId) {
    UUID tenantId = TenantContext.requireTenantId();
    CreditNoteApplication application =
        applicationRepository
            .findByTenantIdAndIdAndIsActiveTrue(tenantId, applicationId)
            .orElseThrow(() -> new FinanceDomainException("Application not found"));

    if (!application.getCreditNoteId().equals(creditNoteId)) {
      throw new FinanceDomainException("Application does not belong to this credit note");
    }

    Invoice targetInvoice = getInvoiceOrThrow(tenantId, application.getTargetInvoiceId());
    targetInvoice.reverseCredit(application.getAmount());
    invoiceRepository.save(targetInvoice);

    Invoice creditNote = getInvoiceOrThrow(tenantId, creditNoteId);
    creditNote.reverseCredit(application.getAmount());
    invoiceRepository.save(creditNote);

    application.delete();
    applicationRepository.save(application);

    eventPublisher.publish(
        new CreditNoteReversedEvent(
            tenantId,
            creditNoteId,
            application.getTargetInvoiceId(),
            application.getAmount(),
            targetInvoice.getPaymentStatus().name()));

    log.info("Reversed credit note application {}", applicationId);
  }

  @Transactional(readOnly = true)
  public List<CreditNoteApplicationDto> getCreditNoteApplications(UUID creditNoteId) {
    return applicationRepository
        .findByTenantIdAndCreditNoteIdAndIsActiveTrue(TenantContext.requireTenantId(), creditNoteId)
        .stream()
        .map(this::toDto)
        .toList();
  }

  private Invoice getInvoiceOrThrow(UUID tenantId, UUID invoiceId) {
    return invoiceRepository
        .findByTenantIdAndId(tenantId, invoiceId)
        .orElseThrow(() -> new FinanceDomainException("Invoice not found: " + invoiceId));
  }

  private CreditNoteApplicationDto toDto(CreditNoteApplication entity) {
    return new CreditNoteApplicationDto(
        entity.getId(),
        entity.getCreditNoteId(),
        entity.getTargetInvoiceId(),
        entity.getAmount().getAmount(),
        entity.getAmount().getCurrency().getCurrencyCode(),
        entity.getAppliedAt());
  }
}
