package com.fabricmanagement.finance.common.app.port.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.common.app.OpenInvoiceAmountService;
import com.fabricmanagement.finance.common.app.OpenInvoiceAmountService.ConvertedAmount;
import com.fabricmanagement.finance.common.app.port.dto.AnalyticsRevenueRecordDto;
import com.fabricmanagement.finance.common.app.port.dto.AnalyticsRevenueResponse;
import com.fabricmanagement.finance.invoice.app.InvoiceSide;
import com.fabricmanagement.finance.invoice.app.InvoiceSideResolver;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsFinancePortImplTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  @Mock private InvoiceRepository invoiceRepository;
  @Mock private OpenInvoiceAmountService openInvoiceAmountService;
  @Mock private InvoiceSideResolver invoiceSideResolver;

  private Clock clock;
  private AnalyticsFinancePortImpl port;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2024-06-15T10:00:00Z"), ZoneId.of("UTC"));
    port =
        new AnalyticsFinancePortImpl(
            invoiceRepository, openInvoiceAmountService, invoiceSideResolver, clock);
  }

  @Test
  void shouldFetchAndConvertInvoicesWithCorrectSigns() {
    LocalDate from = LocalDate.of(2024, 1, 1);
    LocalDate to = LocalDate.of(2024, 6, 15);

    Invoice sales = new Invoice();
    sales.setId(UUID.randomUUID());
    sales.setTradingPartnerId(UUID.randomUUID());
    sales.setInvoiceType(InvoiceType.SALES);
    sales.setIssueDate(LocalDate.of(2024, 2, 1));
    sales.setTotalAmount(Money.of(new BigDecimal("100"), "EUR"));
    sales.setSubtotal(Money.of(new BigDecimal("100"), "EUR"));

    Invoice debitNote = new Invoice();
    debitNote.setId(UUID.randomUUID());
    debitNote.setTradingPartnerId(UUID.randomUUID());
    debitNote.setInvoiceType(InvoiceType.DEBIT_NOTE);
    debitNote.setIssueDate(LocalDate.of(2024, 3, 1));
    debitNote.setTotalAmount(Money.of(new BigDecimal("50"), "EUR"));
    debitNote.setSubtotal(Money.of(new BigDecimal("50"), "EUR"));

    Invoice creditNoteAr = new Invoice();
    creditNoteAr.setId(UUID.randomUUID());
    creditNoteAr.setTradingPartnerId(UUID.randomUUID());
    creditNoteAr.setInvoiceType(InvoiceType.CREDIT_NOTE);
    creditNoteAr.setIssueDate(LocalDate.of(2024, 4, 1));
    creditNoteAr.setTotalAmount(Money.of(new BigDecimal("20"), "EUR"));
    creditNoteAr.setSubtotal(Money.of(new BigDecimal("20"), "EUR"));

    Invoice creditNoteAp = new Invoice(); // Should be skipped
    creditNoteAp.setId(UUID.randomUUID());
    creditNoteAp.setTradingPartnerId(UUID.randomUUID());
    creditNoteAp.setInvoiceType(InvoiceType.CREDIT_NOTE);
    creditNoteAp.setIssueDate(LocalDate.of(2024, 5, 1));
    creditNoteAp.setTotalAmount(Money.of(new BigDecimal("30"), "EUR"));
    creditNoteAp.setSubtotal(Money.of(new BigDecimal("30"), "EUR"));

    when(invoiceRepository.findIssuedInvoicesInWindow(
            TENANT_ID, InvoiceType.SALES, InvoiceStatus.EXCLUDED_FROM_ISSUED_REVENUE, from, to))
        .thenReturn(List.of(sales));
    when(invoiceRepository.findIssuedInvoicesInWindow(
            TENANT_ID,
            InvoiceType.DEBIT_NOTE,
            InvoiceStatus.EXCLUDED_FROM_ISSUED_REVENUE,
            from,
            to))
        .thenReturn(List.of(debitNote));
    when(invoiceRepository.findIssuedInvoicesInWindow(
            TENANT_ID,
            InvoiceType.CREDIT_NOTE,
            InvoiceStatus.EXCLUDED_FROM_ISSUED_REVENUE,
            from,
            to))
        .thenReturn(List.of(creditNoteAr, creditNoteAp));

    when(invoiceSideResolver.resolveSide(TENANT_ID, creditNoteAr))
        .thenReturn(InvoiceSide.ACCOUNTS_RECEIVABLE);
    when(invoiceSideResolver.resolveSide(TENANT_ID, creditNoteAp))
        .thenReturn(InvoiceSide.ACCOUNTS_PAYABLE);

    // Mock FX conversion (assume EUR->USD is * 1.1)
    when(openInvoiceAmountService.convertAmount(
            eq(TENANT_ID), eq(sales), any(BigDecimal.class), eq("EUR"), eq("USD"), any(), any()))
        .thenReturn(new ConvertedAmount(new BigDecimal("110")));

    when(openInvoiceAmountService.convertAmount(
            eq(TENANT_ID),
            eq(debitNote),
            any(BigDecimal.class),
            eq("EUR"),
            eq("USD"),
            any(),
            any()))
        .thenReturn(new ConvertedAmount(new BigDecimal("55")));

    when(openInvoiceAmountService.convertAmount(
            eq(TENANT_ID),
            eq(creditNoteAr),
            any(BigDecimal.class),
            eq("EUR"),
            eq("USD"),
            any(),
            any()))
        .thenReturn(new ConvertedAmount(new BigDecimal("22")));

    AnalyticsRevenueResponse response = port.getIssuedRevenueByCustomer(TENANT_ID, from, to, "USD");

    assertThat(response.records()).hasSize(3); // AP Credit note skipped

    AnalyticsRevenueRecordDto recSales =
        response.records().stream()
            .filter(r -> r.issueDate().getMonthValue() == 2)
            .findFirst()
            .get();
    assertThat(recSales.reportingAmount()).isEqualByComparingTo("110"); // +110

    AnalyticsRevenueRecordDto recDebit =
        response.records().stream()
            .filter(r -> r.issueDate().getMonthValue() == 3)
            .findFirst()
            .get();
    assertThat(recDebit.reportingAmount()).isEqualByComparingTo("55"); // +55

    AnalyticsRevenueRecordDto recCredit =
        response.records().stream()
            .filter(r -> r.issueDate().getMonthValue() == 4)
            .findFirst()
            .get();
    assertThat(recCredit.reportingAmount()).isEqualByComparingTo("-22"); // -22
  }
}
