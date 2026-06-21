package com.fabricmanagement.finance.payables.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.finance.common.app.OpenInvoiceAmountService;
import com.fabricmanagement.finance.common.app.OpenInvoiceAmountService.ConvertedAmount;
import com.fabricmanagement.finance.common.app.OpenInvoiceAmountService.OpenAmountResult;
import com.fabricmanagement.finance.common.dto.FinanceWarningDto;
import com.fabricmanagement.finance.invoice.app.InvoiceSide;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import com.fabricmanagement.finance.payables.dto.DpoDto;
import com.fabricmanagement.finance.payables.dto.PayablesAgingBucketDto;
import com.fabricmanagement.finance.payables.dto.PayablesConcentrationDto;
import com.fabricmanagement.finance.payables.dto.PayablesCurrencyBreakdownDto;
import com.fabricmanagement.finance.payables.dto.PayablesRiskFlagDto;
import com.fabricmanagement.finance.payables.dto.PayablesSummaryDto;
import com.fabricmanagement.finance.payables.dto.PayablesSupplierDto;
import com.fabricmanagement.finance.payables.dto.PayablesWarningDto;
import com.fabricmanagement.finance.payment.domain.PaymentDirection;
import com.fabricmanagement.finance.payment.domain.PaymentStatus;
import com.fabricmanagement.finance.payment.infra.repository.PaymentAllocationRepository;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayablesInsightService {

  private static final int REPORTING_SCALE = 4;
  private static final int PERCENT_SCALE = 4;
  private static final int DPO_WINDOW_DAYS = 90;
  private static final int PAYMENT_BEHAVIOR_WINDOW_DAYS = 365;
  private static final BigDecimal HIGH_CONCENTRATION_PERCENT = new BigDecimal("25.0000");
  private static final List<InvoiceType> AP_TYPES = List.of(InvoiceType.PURCHASE);
  private static final List<InvoiceStatus> EXCLUDED_OPEN_AP_STATUSES =
      List.of(InvoiceStatus.CANCELLED, InvoiceStatus.VOIDED, InvoiceStatus.DRAFT);

  private final InvoiceRepository invoiceRepository;
  private final PaymentAllocationRepository paymentAllocationRepository;
  private final TenantReportingCurrencyPort reportingCurrencyPort;
  private final OpenInvoiceAmountService openInvoiceAmountService;
  private final TradingPartnerResolver tradingPartnerResolver;
  private final Clock clock;

  public PayablesSummaryDto getSummary(Integer topN) {
    UUID tenantId = TenantContext.requireTenantId();
    LocalDate asOfDate = LocalDate.now(clock);
    PayablesModel model = buildModel(tenantId, asOfDate);
    List<PayablesSupplierDto> suppliers = buildSuppliers(tenantId, model);
    int concentrationLimit = topN == null || topN <= 0 ? 5 : topN;

    return new PayablesSummaryDto(
        asOfDate,
        model.reportingCurrency(),
        model.totalOutstanding(),
        model.overdueExposure(),
        percentage(model.overdueExposure(), model.totalOutstanding()),
        agingDtos(model.agingTotals()),
        currencyDtos(model.currencyTotals()),
        suppliers.stream()
            .filter(supplier -> supplier.outstanding().compareTo(BigDecimal.ZERO) > 0)
            .sorted(Comparator.comparing(PayablesSupplierDto::outstanding).reversed())
            .limit(concentrationLimit)
            .map(
                supplier ->
                    new PayablesConcentrationDto(
                        supplier.tradingPartnerId(),
                        supplier.tradingPartnerName(),
                        supplier.outstanding(),
                        supplier.concentrationPercent()))
            .toList(),
        buildDpo(
            tenantId,
            asOfDate,
            model.reportingCurrency(),
            model.totalOutstanding(),
            model.warnings()),
        model.warnings());
  }

  public Page<PayablesSupplierDto> getSuppliers(Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();
    PayablesModel model = buildModel(tenantId, LocalDate.now(clock));
    List<PayablesSupplierDto> suppliers = sortSuppliers(buildSuppliers(tenantId, model), pageable);
    int fromIndex = Math.min((int) pageable.getOffset(), suppliers.size());
    int toIndex = Math.min(fromIndex + pageable.getPageSize(), suppliers.size());
    return new PageImpl<>(suppliers.subList(fromIndex, toIndex), pageable, suppliers.size());
  }

  /**
   * Computes standalone DPO for the given tenant/date/currency. Reuses the existing buildModel +
   * buildDpo pipeline — no formula duplication.
   */
  public DpoDto computeDpo(UUID tenantId, LocalDate asOfDate, String reportingCurrency) {
    PayablesModel model = buildModel(tenantId, asOfDate);
    return buildDpo(
        tenantId, asOfDate, reportingCurrency, model.totalOutstanding(), model.warnings());
  }

  private PayablesModel buildModel(UUID tenantId, LocalDate asOfDate) {
    String reportingCurrency = reportingCurrencyPort.getReportingCurrency(tenantId);
    List<PayablesWarningDto> warnings = new ArrayList<>();
    List<PayableLine> lines =
        invoiceRepository
            .findOpenAccountsPayable(
                tenantId, AP_TYPES, InvoiceType.CREDIT_NOTE, EXCLUDED_OPEN_AP_STATUSES)
            .stream()
            .map(invoice -> toLine(tenantId, invoice, reportingCurrency, asOfDate, warnings))
            .toList();

    BigDecimal totalOutstanding =
        lines.stream()
            .map(PayableLine::signedReportingAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(REPORTING_SCALE, RoundingMode.HALF_UP);

    BigDecimal overdueExposure =
        lines.stream()
            .filter(PayableLine::agingEligible)
            .filter(line -> line.invoice().isOverdue(asOfDate))
            .map(PayableLine::signedReportingAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(REPORTING_SCALE, RoundingMode.HALF_UP);

    Map<AgingBucket, BigDecimal> agingTotals = emptyAgingMap();
    lines.stream()
        .filter(PayableLine::agingEligible)
        .forEach(
            line ->
                agingTotals.merge(
                    bucketFor(line.invoice(), asOfDate),
                    line.signedReportingAmount(),
                    BigDecimal::add));

    Map<String, CurrencyAccumulator> currencyTotals = new HashMap<>();
    lines.forEach(
        line ->
            currencyTotals
                .computeIfAbsent(line.documentCurrency(), ignored -> new CurrencyAccumulator())
                .add(line.signedDocumentAmount(), line.signedReportingAmount()));

    Map<UUID, List<PayableLine>> bySupplier =
        lines.stream().collect(Collectors.groupingBy(line -> line.invoice().getTradingPartnerId()));

    return new PayablesModel(
        asOfDate,
        reportingCurrency,
        lines,
        bySupplier,
        totalOutstanding,
        overdueExposure,
        agingTotals,
        currencyTotals,
        warnings);
  }

  private PayableLine toLine(
      UUID tenantId,
      Invoice invoice,
      String reportingCurrency,
      LocalDate asOfDate,
      List<PayablesWarningDto> warnings) {

    OpenAmountResult openAmount =
        openInvoiceAmountService.signedOpenAmountForSide(
            tenantId, invoice, InvoiceSide.ACCOUNTS_PAYABLE, reportingCurrency, asOfDate);

    for (FinanceWarningDto w : openAmount.warnings()) {
      warnings.add(new PayablesWarningDto(w.code(), w.invoiceId(), w.message()));
    }

    return new PayableLine(
        invoice,
        openAmount.signedDocumentAmount(),
        openAmount.signedReportingAmount(),
        invoice.getCurrency(),
        isAgingEligible(invoice));
  }

  private boolean isAgingEligible(Invoice invoice) {
    return invoice.getInvoiceType().isPayable();
  }

  private List<PayablesSupplierDto> buildSuppliers(UUID tenantId, PayablesModel model) {
    Map<UUID, String> names =
        tradingPartnerResolver.resolveDisplayNames(
            tenantId, new ArrayList<>(model.linesBySupplier().keySet()));
    Map<UUID, BigDecimal> averageDaysLate = averageDaysLate(tenantId, model.asOfDate());

    return model.linesBySupplier().entrySet().stream()
        .map(
            entry -> {
              UUID partnerId = entry.getKey();
              List<PayableLine> lines = entry.getValue();
              BigDecimal outstanding =
                  sum(lines, PayableLine::signedReportingAmount)
                      .setScale(REPORTING_SCALE, RoundingMode.HALF_UP);
              BigDecimal overdue =
                  lines.stream()
                      .filter(PayableLine::agingEligible)
                      .filter(line -> line.invoice().isOverdue(model.asOfDate()))
                      .map(PayableLine::signedReportingAmount)
                      .reduce(BigDecimal.ZERO, BigDecimal::add)
                      .setScale(REPORTING_SCALE, RoundingMode.HALF_UP);
              BigDecimal unappliedCredits =
                  lines.stream()
                      .filter(line -> line.invoice().getInvoiceType() == InvoiceType.CREDIT_NOTE)
                      .map(line -> line.signedReportingAmount().abs())
                      .reduce(BigDecimal.ZERO, BigDecimal::add)
                      .setScale(REPORTING_SCALE, RoundingMode.HALF_UP);
              Map<AgingBucket, BigDecimal> aging = emptyAgingMap();
              lines.stream()
                  .filter(PayableLine::agingEligible)
                  .forEach(
                      line ->
                          aging.merge(
                              bucketFor(line.invoice(), model.asOfDate()),
                              line.signedReportingAmount(),
                              BigDecimal::add));
              Map<String, CurrencyAccumulator> currencyTotals = new HashMap<>();
              lines.forEach(
                  line ->
                      currencyTotals
                          .computeIfAbsent(
                              line.documentCurrency(), ignored -> new CurrencyAccumulator())
                          .add(line.signedDocumentAmount(), line.signedReportingAmount()));
              BigDecimal avgLate =
                  averageDaysLate
                      .getOrDefault(partnerId, BigDecimal.ZERO)
                      .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
              return new PayablesSupplierDto(
                  partnerId,
                  names.getOrDefault(partnerId, "Unknown partner"),
                  outstanding,
                  unappliedCredits,
                  overdue,
                  percentage(outstanding.max(BigDecimal.ZERO), model.totalOutstanding()),
                  lines.stream()
                      .anyMatch(line -> line.invoice().getStatus() == InvoiceStatus.DISPUTED),
                  avgLate,
                  agingDtos(aging),
                  currencyDtos(currencyTotals),
                  riskFlags(
                      lines, outstanding, overdue, model.asOfDate(), model.totalOutstanding()));
            })
        .toList();
  }

  private Map<UUID, BigDecimal> averageDaysLate(UUID tenantId, LocalDate asOfDate) {
    LocalDate fromDate = asOfDate.minusDays(PAYMENT_BEHAVIOR_WINDOW_DAYS);
    record LateDays(long total, long count) {
      LateDays add(long days) {
        return new LateDays(total + days, count + 1);
      }
    }
    Map<UUID, LateDays> totals = new HashMap<>();
    paymentAllocationRepository
        .findPaymentTimingRows(
            tenantId, PaymentDirection.OUTBOUND, PaymentStatus.VOIDED, fromDate, asOfDate)
        .forEach(
            row -> {
              UUID partnerId = (UUID) row[0];
              LocalDate dueDate = (LocalDate) row[1];
              LocalDate paymentDate = (LocalDate) row[2];
              long daysLate = Math.max(0, ChronoUnit.DAYS.between(dueDate, paymentDate));
              totals.merge(
                  partnerId, new LateDays(daysLate, 1), (left, right) -> left.add(right.total()));
            });
    return totals.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                entry ->
                    BigDecimal.valueOf(entry.getValue().total())
                        .divide(
                            BigDecimal.valueOf(entry.getValue().count()),
                            PERCENT_SCALE,
                            RoundingMode.HALF_UP)));
  }

  private DpoDto buildDpo(
      UUID tenantId,
      LocalDate asOfDate,
      String reportingCurrency,
      BigDecimal netAccountsPayable,
      List<PayablesWarningDto> warnings) {
    LocalDate fromDate = asOfDate.minusDays(DPO_WINDOW_DAYS - 1L);
    BigDecimal purchases =
        invoiceRepository
            .findIssuedInvoicesInWindow(
                tenantId, InvoiceType.PURCHASE, EXCLUDED_OPEN_AP_STATUSES, fromDate, asOfDate)
            .stream()
            .map(
                invoice -> {
                  List<FinanceWarningDto> localWarnings = new ArrayList<>();
                  ConvertedAmount converted =
                      openInvoiceAmountService.convertAmount(
                          tenantId,
                          invoice,
                          invoice.getTotalAmount().getAmount().abs(),
                          invoice.getCurrency(),
                          reportingCurrency,
                          asOfDate,
                          localWarnings);
                  for (FinanceWarningDto w : localWarnings) {
                    warnings.add(new PayablesWarningDto(w.code(), w.invoiceId(), w.message()));
                  }
                  return converted.amount();
                })
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(REPORTING_SCALE, RoundingMode.HALF_UP);

    if (purchases.compareTo(BigDecimal.ZERO) == 0) {
      return new DpoDto(
          DPO_WINDOW_DAYS, null, netAccountsPayable, purchases, "INSUFFICIENT_PURCHASE_WINDOW");
    }
    BigDecimal dpo =
        netAccountsPayable
            .multiply(BigDecimal.valueOf(DPO_WINDOW_DAYS))
            .divide(purchases, REPORTING_SCALE, RoundingMode.HALF_UP);
    return new DpoDto(DPO_WINDOW_DAYS, dpo, netAccountsPayable, purchases, "OK");
  }

  private List<PayablesRiskFlagDto> riskFlags(
      List<PayableLine> lines,
      BigDecimal outstanding,
      BigDecimal overdue,
      LocalDate asOfDate,
      BigDecimal totalOutstanding) {
    List<PayablesRiskFlagDto> flags = new ArrayList<>();

    if (overdue.compareTo(BigDecimal.ZERO) > 0) {
      flags.add(new PayablesRiskFlagDto("OVERDUE_PAYABLE", "Supplier has overdue payables"));
    }

    if (percentage(outstanding.max(BigDecimal.ZERO), totalOutstanding)
            .compareTo(HIGH_CONCENTRATION_PERCENT)
        >= 0) {
      flags.add(
          new PayablesRiskFlagDto(
              "HIGH_DEPENDENCY", "Supplier represents at least 25% of total net AP"));
    }

    boolean hasLargeUpcomingOutflow =
        lines.stream()
            .filter(PayableLine::agingEligible)
            .anyMatch(
                line -> {
                  long daysToDue = asOfDate.until(line.invoice().getDueDate(), ChronoUnit.DAYS);
                  if (daysToDue >= 0 && daysToDue <= 7) {
                    return percentage(
                                line.signedReportingAmount().max(BigDecimal.ZERO), totalOutstanding)
                            .compareTo(HIGH_CONCENTRATION_PERCENT)
                        >= 0;
                  }
                  return false;
                });

    if (hasLargeUpcomingOutflow) {
      flags.add(
          new PayablesRiskFlagDto(
              "LARGE_UPCOMING_OUTFLOW",
              "Supplier has an invoice due within 7 days whose reporting amount is ≥ 25% of total net AP"));
    }

    return flags;
  }

  private List<PayablesSupplierDto> sortSuppliers(
      List<PayablesSupplierDto> suppliers, Pageable pageable) {
    Comparator<PayablesSupplierDto> comparator = null;
    for (Sort.Order order : pageable.getSort()) {
      Comparator<PayablesSupplierDto> next = comparatorFor(order.getProperty());
      if (order.isDescending()) {
        next = next.reversed();
      }
      comparator = comparator == null ? next : comparator.thenComparing(next);
    }
    if (comparator == null) {
      comparator = Comparator.comparing(PayablesSupplierDto::outstanding).reversed();
    }
    return suppliers.stream().sorted(comparator).toList();
  }

  private Comparator<PayablesSupplierDto> comparatorFor(String property) {
    return switch (property) {
      case "tradingPartnerName" -> Comparator.comparing(PayablesSupplierDto::tradingPartnerName);
      case "overdueExposure" -> Comparator.comparing(PayablesSupplierDto::overdueExposure);
      case "concentrationPercent" ->
          Comparator.comparing(PayablesSupplierDto::concentrationPercent);
      case "averageDaysLate" -> Comparator.comparing(PayablesSupplierDto::averageDaysLate);
      default -> Comparator.comparing(PayablesSupplierDto::outstanding);
    };
  }

  private AgingBucket bucketFor(Invoice invoice, LocalDate asOfDate) {
    long daysOverdue = invoice.getDaysOverdue(asOfDate);
    if (daysOverdue <= 0) {
      return AgingBucket.CURRENT;
    }
    if (daysOverdue <= 30) {
      return AgingBucket.DAYS_1_30;
    }
    if (daysOverdue <= 60) {
      return AgingBucket.DAYS_31_60;
    }
    if (daysOverdue <= 90) {
      return AgingBucket.DAYS_61_90;
    }
    return AgingBucket.DAYS_90_PLUS;
  }

  private Map<AgingBucket, BigDecimal> emptyAgingMap() {
    Map<AgingBucket, BigDecimal> aging = new EnumMap<>(AgingBucket.class);
    for (AgingBucket bucket : AgingBucket.values()) {
      aging.put(bucket, BigDecimal.ZERO.setScale(REPORTING_SCALE, RoundingMode.HALF_UP));
    }
    return aging;
  }

  private List<PayablesAgingBucketDto> agingDtos(Map<AgingBucket, BigDecimal> aging) {
    return List.of(AgingBucket.values()).stream()
        .map(
            bucket ->
                new PayablesAgingBucketDto(
                    bucket.name(),
                    aging
                        .getOrDefault(bucket, BigDecimal.ZERO)
                        .setScale(REPORTING_SCALE, RoundingMode.HALF_UP)))
        .toList();
  }

  private List<PayablesCurrencyBreakdownDto> currencyDtos(
      Map<String, CurrencyAccumulator> currencyTotals) {
    return currencyTotals.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(
            entry ->
                new PayablesCurrencyBreakdownDto(
                    entry.getKey(),
                    entry
                        .getValue()
                        .documentAmount()
                        .setScale(REPORTING_SCALE, RoundingMode.HALF_UP),
                    entry
                        .getValue()
                        .reportingAmount()
                        .setScale(REPORTING_SCALE, RoundingMode.HALF_UP)))
        .toList();
  }

  private BigDecimal sum(List<PayableLine> lines, Function<PayableLine, BigDecimal> getter) {
    return lines.stream().map(getter).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
    if (denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }
    return numerator
        .multiply(new BigDecimal("100"))
        .divide(denominator, PERCENT_SCALE, RoundingMode.HALF_UP);
  }

  private enum AgingBucket {
    CURRENT,
    DAYS_1_30,
    DAYS_31_60,
    DAYS_61_90,
    DAYS_90_PLUS
  }

  private record PayableLine(
      Invoice invoice,
      BigDecimal signedDocumentAmount,
      BigDecimal signedReportingAmount,
      String documentCurrency,
      boolean agingEligible) {}

  private record PayablesModel(
      LocalDate asOfDate,
      String reportingCurrency,
      List<PayableLine> lines,
      Map<UUID, List<PayableLine>> linesBySupplier,
      BigDecimal totalOutstanding,
      BigDecimal overdueExposure,
      Map<AgingBucket, BigDecimal> agingTotals,
      Map<String, CurrencyAccumulator> currencyTotals,
      List<PayablesWarningDto> warnings) {}

  private static final class CurrencyAccumulator {
    private BigDecimal documentAmount = BigDecimal.ZERO;
    private BigDecimal reportingAmount = BigDecimal.ZERO;

    void add(BigDecimal document, BigDecimal reporting) {
      documentAmount = documentAmount.add(document);
      reportingAmount = reportingAmount.add(reporting);
    }

    BigDecimal documentAmount() {
      return documentAmount;
    }

    BigDecimal reportingAmount() {
      return reportingAmount;
    }
  }
}
