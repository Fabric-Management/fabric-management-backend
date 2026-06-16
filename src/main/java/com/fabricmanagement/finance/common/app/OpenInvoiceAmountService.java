package com.fabricmanagement.finance.common.app;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.domain.exception.ExchangeRateRequiredException;
import com.fabricmanagement.finance.common.dto.FinanceWarningDto;
import com.fabricmanagement.finance.invoice.app.InvoiceSide;
import com.fabricmanagement.finance.invoice.app.InvoiceSideResolver;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OpenInvoiceAmountService {

  private static final int REPORTING_SCALE = 4;

  private final ExchangeRateService exchangeRateService;
  private final InvoiceSideResolver invoiceSideResolver;

  public OpenAmountResult signedOpenAmountForSide(
      UUID tenantId,
      Invoice invoice,
      InvoiceSide expectedSide,
      String reportingCurrency,
      LocalDate asOfDate) {

    BigDecimal documentAmount = invoice.getAmountDue().getAmount().abs();
    List<FinanceWarningDto> warnings = new ArrayList<>();
    ConvertedAmount converted =
        convertAmount(
            tenantId,
            invoice,
            documentAmount,
            invoice.getCurrency(),
            reportingCurrency,
            asOfDate,
            warnings);

    int sign = amountSign(tenantId, invoice, expectedSide);

    return new OpenAmountResult(
        documentAmount
            .multiply(BigDecimal.valueOf(sign))
            .setScale(REPORTING_SCALE, RoundingMode.HALF_UP),
        converted
            .amount()
            .multiply(BigDecimal.valueOf(sign))
            .setScale(REPORTING_SCALE, RoundingMode.HALF_UP),
        warnings);
  }

  public ConvertedAmount convertAmount(
      UUID tenantId,
      Invoice invoice,
      BigDecimal documentAmount,
      String documentCurrency,
      String reportingCurrency,
      LocalDate asOfDate,
      List<FinanceWarningDto> warnings) {

    BigDecimal reportingAmount;
    if (documentCurrency.equalsIgnoreCase(reportingCurrency)) {
      reportingAmount = documentAmount.setScale(REPORTING_SCALE, RoundingMode.HALF_UP);
    } else {
      try {
        ConvertedMoney converted =
            exchangeRateService.convert(
                tenantId, documentAmount, documentCurrency, reportingCurrency, asOfDate);
        if (converted.getRateDate() != null && converted.getRateDate().isBefore(asOfDate)) {
          warnings.add(
              new FinanceWarningDto(
                  "STALE_RATE",
                  invoice.getId(),
                  "Using %s->%s rate from %s for as-of %s"
                      .formatted(
                          documentCurrency, reportingCurrency, converted.getRateDate(), asOfDate)));
        }
        reportingAmount =
            converted.getConvertedAmount().setScale(REPORTING_SCALE, RoundingMode.HALF_UP);
      } catch (ExchangeRateRequiredException ex) {
        if (invoice.getIssueExchangeRate() != null) {
          warnings.add(
              new FinanceWarningDto(
                  "ISSUE_RATE_FALLBACK",
                  invoice.getId(),
                  "Missing %s->%s rate for %s; using issue rate from %s"
                      .formatted(
                          documentCurrency,
                          reportingCurrency,
                          asOfDate,
                          invoice.getIssueExchangeRateDate())));
          reportingAmount =
              documentAmount
                  .multiply(invoice.getIssueExchangeRate())
                  .setScale(REPORTING_SCALE, RoundingMode.HALF_UP);
        } else {
          warnings.add(
              new FinanceWarningDto(
                  "MISSING_RATE",
                  invoice.getId(),
                  "Missing %s->%s rate for %s; using document amount as degraded reporting value"
                      .formatted(documentCurrency, reportingCurrency, asOfDate)));
          reportingAmount = documentAmount.setScale(REPORTING_SCALE, RoundingMode.HALF_UP);
        }
      }
    }
    return new ConvertedAmount(reportingAmount);
  }

  private int amountSign(UUID tenantId, Invoice invoice, InvoiceSide expectedSide) {
    if (expectedSide == InvoiceSide.ACCOUNTS_RECEIVABLE) {
      if (invoice.getInvoiceType().isReceivable()) {
        return 1;
      }
      if (invoice.getInvoiceType() == InvoiceType.CREDIT_NOTE
          && invoiceSideResolver.resolveSide(tenantId, invoice)
              == InvoiceSide.ACCOUNTS_RECEIVABLE) {
        return -1;
      }
    } else if (expectedSide == InvoiceSide.ACCOUNTS_PAYABLE) {
      if (invoice.getInvoiceType().isPayable()) {
        return 1;
      }
      if (invoice.getInvoiceType() == InvoiceType.CREDIT_NOTE
          && invoiceSideResolver.resolveSide(tenantId, invoice) == InvoiceSide.ACCOUNTS_PAYABLE) {
        return -1;
      }
    }
    return 1;
  }

  public record OpenAmountResult(
      BigDecimal signedDocumentAmount,
      BigDecimal signedReportingAmount,
      List<FinanceWarningDto> warnings) {}

  public record ConvertedAmount(BigDecimal amount) {}
}
