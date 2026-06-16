package com.fabricmanagement.finance.invoice.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.fx.app.RealizedFxService;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import com.fabricmanagement.finance.invoice.dto.CreateCreditNoteApplicationRequest;
import com.fabricmanagement.finance.invoice.infra.repository.CreditNoteApplicationRepository;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import com.fabricmanagement.finance.period.app.port.FinancialPeriodGuard;
import com.fabricmanagement.finance.period.domain.exception.ClosedFinancialPeriodException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreditNoteApplicationServiceTest {

  @Mock private InvoiceRepository invoiceRepository;
  @Mock private CreditNoteApplicationRepository applicationRepository;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private InvoiceSideResolver sideResolver;
  @Mock private RealizedFxService realizedFxService;
  @Mock private FinancialPeriodGuard financialPeriodGuard;

  private final UUID tenantId = UUID.randomUUID();
  private final Clock clock = Clock.fixed(Instant.parse("2026-05-20T10:00:00Z"), ZoneOffset.UTC);

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void applyCreditNoteRejectsClosedCurrentPeriodBeforeMutation() {
    UUID creditNoteId = UUID.randomUUID();
    UUID targetInvoiceId = UUID.randomUUID();
    UUID partnerId = UUID.randomUUID();
    Invoice creditNote =
        issuedInvoice(creditNoteId, partnerId, InvoiceType.CREDIT_NOTE, new BigDecimal("40.00"));
    Invoice targetInvoice =
        issuedInvoice(targetInvoiceId, partnerId, InvoiceType.SALES, new BigDecimal("100.00"));
    LocalDate appliedDate = LocalDate.of(2026, 5, 20);

    when(invoiceRepository.findByTenantIdAndId(tenantId, creditNoteId))
        .thenReturn(Optional.of(creditNote));
    when(invoiceRepository.findByTenantIdAndId(tenantId, targetInvoiceId))
        .thenReturn(Optional.of(targetInvoice));
    when(sideResolver.resolveSide(tenantId, creditNote))
        .thenReturn(InvoiceSide.ACCOUNTS_RECEIVABLE);
    when(sideResolver.resolveSide(tenantId, targetInvoice))
        .thenReturn(InvoiceSide.ACCOUNTS_RECEIVABLE);
    when(applicationRepository.sumAppliedAmount(tenantId, creditNoteId))
        .thenReturn(BigDecimal.ZERO);
    doThrow(new ClosedFinancialPeriodException(appliedDate, YearMonth.of(2026, 5)))
        .when(financialPeriodGuard)
        .assertPostingAllowed(tenantId, appliedDate);

    assertThatThrownBy(
            () ->
                service()
                    .applyCreditNote(
                        creditNoteId,
                        new CreateCreditNoteApplicationRequest(
                            targetInvoiceId, new BigDecimal("40.00"))))
        .isInstanceOf(ClosedFinancialPeriodException.class);

    assertThat(targetInvoice.getAmountCredited()).isEqualTo(Money.zero("USD"));
    assertThat(creditNote.getAmountCredited()).isEqualTo(Money.zero("USD"));
    verify(invoiceRepository, never()).save(any());
    verify(applicationRepository, never()).save(any());
    verify(realizedFxService, never())
        .recordCreditNoteApplication(any(), any(), any(), any(), any());
    verify(eventPublisher, never()).publish(any());
  }

  private CreditNoteApplicationService service() {
    return new CreditNoteApplicationService(
        invoiceRepository,
        applicationRepository,
        eventPublisher,
        clock,
        sideResolver,
        realizedFxService,
        financialPeriodGuard);
  }

  private Invoice issuedInvoice(
      UUID invoiceId, UUID partnerId, InvoiceType type, BigDecimal totalAmount) {
    Invoice invoice =
        Invoice.builder()
            .tradingPartnerId(partnerId)
            .invoiceNumber("INV-" + invoiceId)
            .invoiceType(type)
            .issueDate(LocalDate.of(2026, 4, 1))
            .dueDate(LocalDate.of(2026, 5, 1))
            .subtotal(Money.of(totalAmount, "USD"))
            .taxAmount(Money.zero("USD"))
            .discountAmount(Money.zero("USD"))
            .totalAmount(Money.of(totalAmount, "USD"))
            .build();
    invoice.setId(invoiceId);
    invoice.setTenantId(tenantId);
    invoice.issue();
    invoice.send();
    return invoice;
  }
}
