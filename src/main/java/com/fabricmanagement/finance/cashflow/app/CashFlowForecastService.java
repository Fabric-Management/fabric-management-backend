package com.fabricmanagement.finance.cashflow.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.finance.cashflow.dto.CashFlowBucketDto;
import com.fabricmanagement.finance.cashflow.dto.CashFlowForecastDto;
import com.fabricmanagement.finance.cashflow.dto.CashFlowWarningDto;
import com.fabricmanagement.finance.common.app.OpenInvoiceAmountService;
import com.fabricmanagement.finance.common.app.OpenInvoiceAmountService.OpenAmountResult;
import com.fabricmanagement.finance.common.dto.FinanceWarningDto;
import com.fabricmanagement.finance.invoice.app.InvoiceSide;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CashFlowForecastService {

  private static final int REPORTING_SCALE = 4;
  private static final int DEFAULT_HORIZON_WEEKS = 13;
  private static final int MAX_HORIZON_WEEKS = 52;

  private static final List<InvoiceType> AR_TYPES =
      List.of(InvoiceType.SALES, InvoiceType.DEBIT_NOTE);
  private static final List<InvoiceType> AP_TYPES = List.of(InvoiceType.PURCHASE);
  private static final List<InvoiceStatus> EXCLUDED_OPEN_STATUSES =
      List.of(InvoiceStatus.CANCELLED, InvoiceStatus.VOIDED, InvoiceStatus.DRAFT);

  private final InvoiceRepository invoiceRepository;
  private final TenantReportingCurrencyPort reportingCurrencyPort;
  private final OpenInvoiceAmountService openInvoiceAmountService;
  private final Clock clock;

  public CashFlowForecastDto generateForecast(
      BigDecimal openingBalance, Integer horizonWeeksParam) {
    UUID tenantId = TenantContext.requireTenantId();
    LocalDate asOfDate = LocalDate.now(clock);
    String reportingCurrency = reportingCurrencyPort.getReportingCurrency(tenantId);

    int horizonWeeks =
        (horizonWeeksParam == null || horizonWeeksParam < 1)
            ? DEFAULT_HORIZON_WEEKS
            : Math.min(horizonWeeksParam, MAX_HORIZON_WEEKS);
    String mode = openingBalance == null ? "NET_MOVEMENT" : "PROJECTED_POSITION";

    List<CashFlowWarningDto> warnings = new ArrayList<>();

    List<Invoice> openArInvoices =
        invoiceRepository.findOpenAccountsReceivable(
            tenantId, AR_TYPES, InvoiceType.CREDIT_NOTE, EXCLUDED_OPEN_STATUSES);

    List<Invoice> openApInvoices =
        invoiceRepository.findOpenAccountsPayable(
            tenantId, AP_TYPES, InvoiceType.CREDIT_NOTE, EXCLUDED_OPEN_STATUSES);

    // Initialize buckets
    List<BucketData> bucketDataList = new ArrayList<>(horizonWeeks + 1);

    // OVERDUE_NOW bucket
    bucketDataList.add(new BucketData("OVERDUE_NOW", "Overdue / Now", null, asOfDate));

    LocalDate currentStart = asOfDate.plusDays(1);
    for (int i = 1; i <= horizonWeeks; i++) {
      LocalDate currentEnd = currentStart.plusDays(6);
      bucketDataList.add(new BucketData("WEEK_" + i, "Week " + i, currentStart, currentEnd));
      currentStart = currentEnd.plusDays(1);
    }
    LocalDate horizonEndDate = bucketDataList.get(bucketDataList.size() - 1).endDate;

    // Process AR (Inflows)
    for (Invoice invoice : openArInvoices) {
      processInvoice(
          tenantId,
          invoice,
          InvoiceSide.ACCOUNTS_RECEIVABLE,
          reportingCurrency,
          asOfDate,
          bucketDataList,
          warnings,
          true);
    }

    // Process AP (Outflows)
    for (Invoice invoice : openApInvoices) {
      processInvoice(
          tenantId,
          invoice,
          InvoiceSide.ACCOUNTS_PAYABLE,
          reportingCurrency,
          asOfDate,
          bucketDataList,
          warnings,
          false);
    }

    // Finalize buckets
    List<CashFlowBucketDto> buckets = new ArrayList<>(bucketDataList.size());
    BigDecimal cumulativeNet = BigDecimal.ZERO;
    BigDecimal projectedPos = openingBalance;
    boolean hasCrunch = false;
    String firstCrunchBucketKey = null;

    for (BucketData bd : bucketDataList) {
      BigDecimal netMovement = bd.inflows.subtract(bd.outflows);
      cumulativeNet = cumulativeNet.add(netMovement);

      boolean isCrunch = false;
      if (openingBalance != null) {
        projectedPos = projectedPos.add(netMovement);
        if (projectedPos.compareTo(BigDecimal.ZERO) < 0) {
          isCrunch = true;
          hasCrunch = true;
          if (firstCrunchBucketKey == null) {
            firstCrunchBucketKey = bd.key;
          }
        }
      }

      buckets.add(
          new CashFlowBucketDto(
              bd.key,
              bd.label,
              bd.startDate,
              bd.endDate,
              bd.inflows.setScale(REPORTING_SCALE, RoundingMode.HALF_UP),
              bd.outflows.setScale(REPORTING_SCALE, RoundingMode.HALF_UP),
              netMovement.setScale(REPORTING_SCALE, RoundingMode.HALF_UP),
              cumulativeNet.setScale(REPORTING_SCALE, RoundingMode.HALF_UP),
              openingBalance == null
                  ? null
                  : projectedPos.setScale(REPORTING_SCALE, RoundingMode.HALF_UP),
              isCrunch,
              isCrunch && bd.key.equals(firstCrunchBucketKey)));
    }

    return new CashFlowForecastDto(
        asOfDate,
        reportingCurrency,
        mode,
        openingBalance,
        horizonWeeks,
        horizonEndDate,
        buckets,
        firstCrunchBucketKey,
        hasCrunch,
        warnings);
  }

  private void processInvoice(
      UUID tenantId,
      Invoice invoice,
      InvoiceSide side,
      String reportingCurrency,
      LocalDate asOfDate,
      List<BucketData> bucketDataList,
      List<CashFlowWarningDto> warnings,
      boolean isInflow) {

    OpenAmountResult amountResult =
        openInvoiceAmountService.signedOpenAmountForSide(
            tenantId, invoice, side, reportingCurrency, asOfDate);

    for (FinanceWarningDto w : amountResult.warnings()) {
      warnings.add(new CashFlowWarningDto(w.code(), w.invoiceId(), w.message()));
    }

    LocalDate dueDate = invoice.getDueDate();

    // Find bucket
    BucketData targetBucket = null;
    if (dueDate.compareTo(asOfDate) <= 0) {
      targetBucket = bucketDataList.get(0);
    } else {
      for (int i = 1; i < bucketDataList.size(); i++) {
        BucketData bd = bucketDataList.get(i);
        if (dueDate.compareTo(bd.startDate) >= 0 && dueDate.compareTo(bd.endDate) <= 0) {
          targetBucket = bd;
          break;
        }
      }
    }

    if (targetBucket != null) {
      if (isInflow) {
        targetBucket.inflows = targetBucket.inflows.add(amountResult.signedReportingAmount());
      } else {
        targetBucket.outflows = targetBucket.outflows.add(amountResult.signedReportingAmount());
      }
    }
  }

  private static class BucketData {
    String key;
    String label;
    LocalDate startDate;
    LocalDate endDate;
    BigDecimal inflows = BigDecimal.ZERO;
    BigDecimal outflows = BigDecimal.ZERO;

    BucketData(String key, String label, LocalDate startDate, LocalDate endDate) {
      this.key = key;
      this.label = label;
      this.startDate = startDate;
      this.endDate = endDate;
    }
  }
}
