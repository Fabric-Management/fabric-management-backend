package com.fabricmanagement.finance.receivables.app;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.domain.exception.ExchangeRateRequiredException;
import com.fabricmanagement.finance.invoice.app.InvoiceSide;
import com.fabricmanagement.finance.invoice.app.InvoiceSideResolver;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import com.fabricmanagement.finance.payment.domain.PaymentDirection;
import com.fabricmanagement.finance.payment.domain.PaymentStatus;
import com.fabricmanagement.finance.payment.infra.repository.PaymentAllocationRepository;
import com.fabricmanagement.finance.receivables.dto.AgingBucketDto;
import com.fabricmanagement.finance.receivables.dto.DsoDto;
import com.fabricmanagement.finance.receivables.dto.ReceivablesConcentrationDto;
import com.fabricmanagement.finance.receivables.dto.ReceivablesCurrencyBreakdownDto;
import com.fabricmanagement.finance.receivables.dto.ReceivablesCustomerDto;
import com.fabricmanagement.finance.receivables.dto.ReceivablesRiskFlagDto;
import com.fabricmanagement.finance.receivables.dto.ReceivablesSummaryDto;
import com.fabricmanagement.finance.receivables.dto.ReceivablesWarningDto;
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
public class ReceivablesInsightService {

  private static final int REPORTING_SCALE = 4;
  private static final int PERCENT_SCALE = 4;
  private static final int DSO_WINDOW_DAYS = 90;
  private static final int PAYMENT_BEHAVIOR_WINDOW_DAYS = 365;
  private static final BigDecimal HIGH_CONCENTRATION_PERCENT = new BigDecimal("25.0000");
  private static final BigDecimal SLOW_PAYER_DAYS = new BigDecimal("10.0000");
  private static final List<InvoiceType> AR_TYPES =
      List.of(InvoiceType.SALES, InvoiceType.DEBIT_NOTE);
  private static final List<InvoiceStatus> EXCLUDED_OPEN_AR_STATUSES =
      List.of(InvoiceStatus.CANCELLED, InvoiceStatus.VOIDED, InvoiceStatus.DRAFT);

  private final InvoiceRepository invoiceRepository;
  private final PaymentAllocationRepository paymentAllocationRepository;
  private final TenantReportingCurrencyPort reportingCurrencyPort;
  private final ExchangeRateService exchangeRateService;
  private final InvoiceSideResolver invoiceSideResolver;
  private final TradingPartnerResolver tradingPartnerResolver;
  private final Clock clock;

  public ReceivablesSummaryDto getSummary(Integer topN) {
    UUID tenantId = TenantContext.requireTenantId();
    LocalDate asOfDate = LocalDate.now(clock);
    ReceivablesModel model = buildModel(tenantId, asOfDate);
    List<ReceivablesCustomerDto> customers = buildCustomers(tenantId, model);
    int concentrationLimit = topN == null || topN <= 0 ? 5 : topN;

    return new ReceivablesSummaryDto(
        asOfDate,
        model.reportingCurrency(),
        model.totalOutstanding(),
        model.overdueExposure(),
        percentage(model.overdueExposure(), model.totalOutstanding()),
        agingDtos(model.agingTotals()),
        currencyDtos(model.currencyTotals()),
        customers.stream()
            .filter(customer -> customer.outstanding().compareTo(BigDecimal.ZERO) > 0)
            .sorted(Comparator.comparing(ReceivablesCustomerDto::outstanding).reversed())
            .limit(concentrationLimit)
            .map(
                customer ->
                    new ReceivablesConcentrationDto(
                        customer.tradingPartnerId(),
                        customer.tradingPartnerName(),
                        customer.outstanding(),
                        customer.concentrationPercent()))
            .toList(),
        buildDso(
            tenantId,
            asOfDate,
            model.reportingCurrency(),
            model.totalOutstanding(),
            model.warnings()),
        model.warnings());
  }

  public Page<ReceivablesCustomerDto> getCustomers(Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();
    ReceivablesModel model = buildModel(tenantId, LocalDate.now(clock));
    List<ReceivablesCustomerDto> customers =
        sortCustomers(buildCustomers(tenantId, model), pageable);
    int fromIndex = Math.min((int) pageable.getOffset(), customers.size());
    int toIndex = Math.min(fromIndex + pageable.getPageSize(), customers.size());
    return new PageImpl<>(customers.subList(fromIndex, toIndex), pageable, customers.size());
  }

  private ReceivablesModel buildModel(UUID tenantId, LocalDate asOfDate) {
    String reportingCurrency = reportingCurrencyPort.getReportingCurrency(tenantId);
    List<ReceivablesWarningDto> warnings = new ArrayList<>();
    List<ReceivableLine> lines =
        invoiceRepository
            .findOpenAccountsReceivable(
                tenantId, AR_TYPES, InvoiceType.CREDIT_NOTE, EXCLUDED_OPEN_AR_STATUSES)
            .stream()
            .map(invoice -> toLine(tenantId, invoice, reportingCurrency, asOfDate, warnings))
            .toList();

    BigDecimal totalOutstanding =
        lines.stream()
            .map(ReceivableLine::signedReportingAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(REPORTING_SCALE, RoundingMode.HALF_UP);
    BigDecimal overdueExposure =
        lines.stream()
            .filter(ReceivableLine::agingEligible)
            .filter(line -> line.invoice().isOverdue(asOfDate))
            .map(ReceivableLine::signedReportingAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(REPORTING_SCALE, RoundingMode.HALF_UP);

    Map<AgingBucket, BigDecimal> agingTotals = emptyAgingMap();
    lines.stream()
        .filter(ReceivableLine::agingEligible)
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

    Map<UUID, List<ReceivableLine>> byCustomer =
        lines.stream().collect(Collectors.groupingBy(line -> line.invoice().getTradingPartnerId()));

    return new ReceivablesModel(
        asOfDate,
        reportingCurrency,
        lines,
        byCustomer,
        totalOutstanding,
        overdueExposure,
        agingTotals,
        currencyTotals,
        warnings);
  }

  private ReceivableLine toLine(
      UUID tenantId,
      Invoice invoice,
      String reportingCurrency,
      LocalDate asOfDate,
      List<ReceivablesWarningDto> warnings) {
    BigDecimal documentAmount = invoice.getAmountDue().getAmount().abs();
    String documentCurrency = invoice.getCurrency();
    ConvertedAmount converted =
        convertAmount(
            tenantId,
            invoice,
            documentAmount,
            documentCurrency,
            reportingCurrency,
            asOfDate,
            warnings);
    SignedOpenAmount signed =
        signedOpenAmount(tenantId, invoice, documentAmount, converted.amount());
    return new ReceivableLine(
        invoice,
        signed.documentAmount(),
        signed.reportingAmount(),
        documentCurrency,
        isAgingEligible(invoice));
  }

  private SignedOpenAmount signedOpenAmount(
      UUID tenantId, Invoice invoice, BigDecimal documentAmount, BigDecimal reportingAmount) {
    int sign = amountSign(tenantId, invoice);
    return new SignedOpenAmount(
        documentAmount
            .multiply(BigDecimal.valueOf(sign))
            .setScale(REPORTING_SCALE, RoundingMode.HALF_UP),
        reportingAmount
            .multiply(BigDecimal.valueOf(sign))
            .setScale(REPORTING_SCALE, RoundingMode.HALF_UP));
  }

  private ConvertedAmount convertAmount(
      UUID tenantId,
      Invoice invoice,
      BigDecimal documentAmount,
      String documentCurrency,
      String reportingCurrency,
      LocalDate asOfDate,
      List<ReceivablesWarningDto> warnings) {
    if (documentCurrency.equalsIgnoreCase(reportingCurrency)) {
      return new ConvertedAmount(documentAmount.setScale(REPORTING_SCALE, RoundingMode.HALF_UP));
    }
    try {
      ConvertedMoney converted =
          exchangeRateService.convert(
              tenantId, documentAmount, documentCurrency, reportingCurrency, asOfDate);
      if (converted.getRateDate() != null && converted.getRateDate().isBefore(asOfDate)) {
        warnings.add(
            new ReceivablesWarningDto(
                "STALE_RATE",
                invoice.getId(),
                "Using %s->%s rate from %s for as-of %s"
                    .formatted(
                        documentCurrency, reportingCurrency, converted.getRateDate(), asOfDate)));
      }
      return new ConvertedAmount(
          converted.getConvertedAmount().setScale(REPORTING_SCALE, RoundingMode.HALF_UP));
    } catch (ExchangeRateRequiredException ex) {
      if (invoice.getIssueExchangeRate() != null) {
        warnings.add(
            new ReceivablesWarningDto(
                "ISSUE_RATE_FALLBACK",
                invoice.getId(),
                "Missing %s->%s rate for %s; using issue rate from %s"
                    .formatted(
                        documentCurrency,
                        reportingCurrency,
                        asOfDate,
                        invoice.getIssueExchangeRateDate())));
        return new ConvertedAmount(
            documentAmount
                .multiply(invoice.getIssueExchangeRate())
                .setScale(REPORTING_SCALE, RoundingMode.HALF_UP));
      }
      warnings.add(
          new ReceivablesWarningDto(
              "MISSING_RATE",
              invoice.getId(),
              "Missing %s->%s rate for %s; using document amount as degraded reporting value"
                  .formatted(documentCurrency, reportingCurrency, asOfDate)));
      return new ConvertedAmount(documentAmount.setScale(REPORTING_SCALE, RoundingMode.HALF_UP));
    }
  }

  private int amountSign(UUID tenantId, Invoice invoice) {
    if (invoice.getInvoiceType().isReceivable()) {
      return 1;
    }
    if (invoice.getInvoiceType() == InvoiceType.CREDIT_NOTE
        && invoiceSideResolver.resolveSide(tenantId, invoice) == InvoiceSide.ACCOUNTS_RECEIVABLE) {
      return -1;
    }
    return 1;
  }

  private boolean isAgingEligible(Invoice invoice) {
    return invoice.getInvoiceType().isReceivable();
  }

  private List<ReceivablesCustomerDto> buildCustomers(UUID tenantId, ReceivablesModel model) {
    Map<UUID, String> names =
        tradingPartnerResolver.resolveDisplayNames(
            tenantId, new ArrayList<>(model.linesByCustomer().keySet()));
    Map<UUID, BigDecimal> averageDaysLate = averageDaysLate(tenantId, model.asOfDate());

    return model.linesByCustomer().entrySet().stream()
        .map(
            entry -> {
              UUID partnerId = entry.getKey();
              List<ReceivableLine> lines = entry.getValue();
              BigDecimal outstanding =
                  sum(lines, ReceivableLine::signedReportingAmount)
                      .setScale(REPORTING_SCALE, RoundingMode.HALF_UP);
              BigDecimal overdue =
                  lines.stream()
                      .filter(ReceivableLine::agingEligible)
                      .filter(line -> line.invoice().isOverdue(model.asOfDate()))
                      .map(ReceivableLine::signedReportingAmount)
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
                  .filter(ReceivableLine::agingEligible)
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
              return new ReceivablesCustomerDto(
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
                      lines,
                      outstanding,
                      overdue,
                      avgLate,
                      model.asOfDate(),
                      model.totalOutstanding()));
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
            tenantId, PaymentDirection.INBOUND, PaymentStatus.VOIDED, fromDate, asOfDate)
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

  private DsoDto buildDso(
      UUID tenantId,
      LocalDate asOfDate,
      String reportingCurrency,
      BigDecimal netAccountsReceivable,
      List<ReceivablesWarningDto> warnings) {
    LocalDate fromDate = asOfDate.minusDays(DSO_WINDOW_DAYS - 1L);
    BigDecimal creditSales =
        invoiceRepository
            .findIssuedInvoicesInWindow(
                tenantId, InvoiceType.SALES, EXCLUDED_OPEN_AR_STATUSES, fromDate, asOfDate)
            .stream()
            .map(
                invoice ->
                    convertAmount(
                            tenantId,
                            invoice,
                            invoice.getTotalAmount().getAmount().abs(),
                            invoice.getCurrency(),
                            reportingCurrency,
                            asOfDate,
                            warnings)
                        .amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(REPORTING_SCALE, RoundingMode.HALF_UP);
    if (creditSales.compareTo(BigDecimal.ZERO) == 0) {
      return new DsoDto(
          DSO_WINDOW_DAYS, null, netAccountsReceivable, creditSales, "INSUFFICIENT_SALES_WINDOW");
    }
    BigDecimal dso =
        netAccountsReceivable
            .multiply(BigDecimal.valueOf(DSO_WINDOW_DAYS))
            .divide(creditSales, REPORTING_SCALE, RoundingMode.HALF_UP);
    return new DsoDto(DSO_WINDOW_DAYS, dso, netAccountsReceivable, creditSales, "OK");
  }

  private List<ReceivablesRiskFlagDto> riskFlags(
      List<ReceivableLine> lines,
      BigDecimal outstanding,
      BigDecimal overdue,
      BigDecimal averageDaysLate,
      LocalDate asOfDate,
      BigDecimal totalOutstanding) {
    List<ReceivablesRiskFlagDto> flags = new ArrayList<>();
    if (overdue.compareTo(BigDecimal.ZERO) > 0) {
      flags.add(new ReceivablesRiskFlagDto("OVERDUE_BALANCE", "Customer has overdue receivables"));
    }
    boolean severeOverdue =
        lines.stream()
            .filter(ReceivableLine::agingEligible)
            .anyMatch(line -> line.invoice().getDaysOverdue(asOfDate) >= 61);
    if (severeOverdue) {
      flags.add(
          new ReceivablesRiskFlagDto("SEVERE_OVERDUE", "Customer has receivables 61+ days late"));
    }
    if (percentage(outstanding.max(BigDecimal.ZERO), totalOutstanding)
            .compareTo(HIGH_CONCENTRATION_PERCENT)
        >= 0) {
      flags.add(
          new ReceivablesRiskFlagDto(
              "HIGH_CONCENTRATION", "Customer represents at least 25% of total net AR"));
    }
    if (averageDaysLate.compareTo(SLOW_PAYER_DAYS) > 0) {
      flags.add(
          new ReceivablesRiskFlagDto(
              "SLOW_PAYER", "Average historical payment lateness is greater than 10 days"));
    }
    return flags;
  }

  private List<ReceivablesCustomerDto> sortCustomers(
      List<ReceivablesCustomerDto> customers, Pageable pageable) {
    Comparator<ReceivablesCustomerDto> comparator = null;
    for (Sort.Order order : pageable.getSort()) {
      Comparator<ReceivablesCustomerDto> next = comparatorFor(order.getProperty());
      if (order.isDescending()) {
        next = next.reversed();
      }
      comparator = comparator == null ? next : comparator.thenComparing(next);
    }
    if (comparator == null) {
      comparator = Comparator.comparing(ReceivablesCustomerDto::outstanding).reversed();
    }
    return customers.stream().sorted(comparator).toList();
  }

  private Comparator<ReceivablesCustomerDto> comparatorFor(String property) {
    return switch (property) {
      case "tradingPartnerName" -> Comparator.comparing(ReceivablesCustomerDto::tradingPartnerName);
      case "overdueExposure" -> Comparator.comparing(ReceivablesCustomerDto::overdueExposure);
      case "concentrationPercent" ->
          Comparator.comparing(ReceivablesCustomerDto::concentrationPercent);
      case "averageDaysLate" -> Comparator.comparing(ReceivablesCustomerDto::averageDaysLate);
      default -> Comparator.comparing(ReceivablesCustomerDto::outstanding);
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

  private List<AgingBucketDto> agingDtos(Map<AgingBucket, BigDecimal> aging) {
    return List.of(AgingBucket.values()).stream()
        .map(
            bucket ->
                new AgingBucketDto(
                    bucket.name(),
                    aging
                        .getOrDefault(bucket, BigDecimal.ZERO)
                        .setScale(REPORTING_SCALE, RoundingMode.HALF_UP)))
        .toList();
  }

  private List<ReceivablesCurrencyBreakdownDto> currencyDtos(
      Map<String, CurrencyAccumulator> currencyTotals) {
    return currencyTotals.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(
            entry ->
                new ReceivablesCurrencyBreakdownDto(
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

  private BigDecimal sum(List<ReceivableLine> lines, Function<ReceivableLine, BigDecimal> getter) {
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

  private record ConvertedAmount(BigDecimal amount) {}

  private record SignedOpenAmount(BigDecimal documentAmount, BigDecimal reportingAmount) {}

  private record ReceivableLine(
      Invoice invoice,
      BigDecimal signedDocumentAmount,
      BigDecimal signedReportingAmount,
      String documentCurrency,
      boolean agingEligible) {}

  private record ReceivablesModel(
      LocalDate asOfDate,
      String reportingCurrency,
      List<ReceivableLine> lines,
      Map<UUID, List<ReceivableLine>> linesByCustomer,
      BigDecimal totalOutstanding,
      BigDecimal overdueExposure,
      Map<AgingBucket, BigDecimal> agingTotals,
      Map<String, CurrencyAccumulator> currencyTotals,
      List<ReceivablesWarningDto> warnings) {}

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
