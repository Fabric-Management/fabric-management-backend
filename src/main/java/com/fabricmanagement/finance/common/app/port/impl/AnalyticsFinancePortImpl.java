package com.fabricmanagement.finance.common.app.port.impl;

import com.fabricmanagement.finance.common.app.OpenInvoiceAmountService;
import com.fabricmanagement.finance.common.app.OpenInvoiceAmountService.ConvertedAmount;
import com.fabricmanagement.finance.common.app.port.AnalyticsFinancePort;
import com.fabricmanagement.finance.common.app.port.dto.AnalyticsRevenueRecordDto;
import com.fabricmanagement.finance.common.app.port.dto.AnalyticsRevenueResponse;
import com.fabricmanagement.finance.common.dto.FinanceWarningDto;
import com.fabricmanagement.finance.invoice.app.InvoiceSide;
import com.fabricmanagement.finance.invoice.app.InvoiceSideResolver;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalyticsFinancePortImpl implements AnalyticsFinancePort {

  private static final BigDecimal NEGATIVE_ONE = BigDecimal.ONE.negate();

  private final InvoiceRepository invoiceRepository;
  private final OpenInvoiceAmountService openInvoiceAmountService;
  private final InvoiceSideResolver invoiceSideResolver;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public AnalyticsRevenueResponse getIssuedRevenueByCustomer(
      UUID tenantId, LocalDate fromDate, LocalDate toDate, String reportingCurrency) {

    List<FinanceWarningDto> warnings = new ArrayList<>();
    LocalDate asOfDate = LocalDate.now(clock);

    List<Invoice> salesInvoices =
        invoiceRepository.findIssuedInvoicesInWindow(
            tenantId,
            InvoiceType.SALES,
            InvoiceStatus.EXCLUDED_FROM_ISSUED_REVENUE,
            fromDate,
            toDate);

    List<Invoice> debitNotes =
        invoiceRepository.findIssuedInvoicesInWindow(
            tenantId,
            InvoiceType.DEBIT_NOTE,
            InvoiceStatus.EXCLUDED_FROM_ISSUED_REVENUE,
            fromDate,
            toDate);

    List<Invoice> creditNotes =
        invoiceRepository.findIssuedInvoicesInWindow(
            tenantId,
            InvoiceType.CREDIT_NOTE,
            InvoiceStatus.EXCLUDED_FROM_ISSUED_REVENUE,
            fromDate,
            toDate);

    List<AnalyticsRevenueRecordDto> records =
        Stream.concat(
                Stream.concat(salesInvoices.stream(), debitNotes.stream()), creditNotes.stream())
            .map(
                invoice -> processInvoice(tenantId, invoice, reportingCurrency, asOfDate, warnings))
            .filter(record -> record != null)
            .toList();

    return new AnalyticsRevenueResponse(records, warnings);
  }

  private AnalyticsRevenueRecordDto processInvoice(
      UUID tenantId,
      Invoice invoice,
      String reportingCurrency,
      LocalDate asOfDate,
      List<FinanceWarningDto> warnings) {

    BigDecimal sign = determineSign(tenantId, invoice);
    if (sign.compareTo(BigDecimal.ZERO) == 0) {
      return null; // Skip if not an AR revenue invoice
    }

    ConvertedAmount converted =
        openInvoiceAmountService.convertAmount(
            tenantId,
            invoice,
            invoice.getTotalAmount().getAmount().abs(),
            invoice.getCurrency(),
            reportingCurrency,
            asOfDate,
            warnings);

    BigDecimal reportingAmount = converted.amount().multiply(sign);

    return AnalyticsRevenueRecordDto.builder()
        .customerId(invoice.getTradingPartnerId())
        .issueDate(invoice.getIssueDate())
        .reportingAmount(reportingAmount)
        .build();
  }

  private BigDecimal determineSign(UUID tenantId, Invoice invoice) {
    if (invoice.getInvoiceType() == InvoiceType.SALES
        || invoice.getInvoiceType() == InvoiceType.DEBIT_NOTE) {
      return BigDecimal.ONE;
    }
    if (invoice.getInvoiceType() == InvoiceType.CREDIT_NOTE) {
      InvoiceSide side = invoiceSideResolver.resolveSide(tenantId, invoice);
      if (side == InvoiceSide.ACCOUNTS_RECEIVABLE) {
        return NEGATIVE_ONE;
      }
    }
    return BigDecimal.ZERO;
  }
}
