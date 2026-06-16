package com.fabricmanagement.finance.fx.app;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.domain.exception.ExchangeRateRequiredException;
import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import com.fabricmanagement.finance.fx.domain.FxRevaluation;
import com.fabricmanagement.finance.fx.domain.FxRevaluationEntryType;
import com.fabricmanagement.finance.fx.infra.repository.FxRevaluationRepository;
import com.fabricmanagement.finance.invoice.app.InvoiceReportingSnapshotService;
import com.fabricmanagement.finance.invoice.app.InvoiceSide;
import com.fabricmanagement.finance.invoice.app.InvoiceSideResolver;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import com.fabricmanagement.finance.period.domain.FinancialPeriod;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UnrealizedFxService {

  private static final int REPORTING_AMOUNT_SCALE = 4;
  private static final List<InvoiceStatus> REVALUABLE_STATUSES =
      List.of(InvoiceStatus.ISSUED, InvoiceStatus.SENT, InvoiceStatus.DISPUTED);

  private final FxRevaluationRepository fxRevaluationRepository;
  private final InvoiceRepository invoiceRepository;
  private final InvoiceReportingSnapshotService snapshotService;
  private final InvoiceSideResolver sideResolver;
  private final ExchangeRateService exchangeRateService;
  private final Clock clock;

  public CloseResult closePeriod(
      UUID tenantId,
      FinancialPeriod period,
      FinancialPeriod previousClosedPeriod,
      String reportingCurrency) {
    Instant now = Instant.now(clock);
    List<FxRevaluation> reversals =
        previousClosedPeriod == null
            ? List.of()
            : fxRevaluationRepository
                .findUnreversedEntriesForPeriodByType(
                    tenantId, previousClosedPeriod.getId(), FxRevaluationEntryType.REVALUATION)
                .stream()
                .map(entry -> entry.reversal(period.getId(), now))
                .toList();

    List<Invoice> invoices =
        invoiceRepository.findOpenForeignCurrencyForRevaluation(
            tenantId, reportingCurrency, REVALUABLE_STATUSES);

    List<String> missingRates = new ArrayList<>();
    List<FxRevaluation> revaluations = new ArrayList<>();
    for (Invoice invoice : invoices) {
      try {
        revaluations.add(revalueInvoice(tenantId, period, invoice, reportingCurrency, now));
      } catch (ExchangeRateRequiredException ex) {
        missingRates.add(
            "%s->%s for %s (invoice %s)"
                .formatted(
                    ex.getFromCurrency(), ex.getToCurrency(), ex.getDate(), invoice.getId()));
      }
    }

    if (!missingRates.isEmpty()) {
      throw new FinanceDomainException(
          "Cannot close financial period; missing exchange rates: "
              + String.join("; ", missingRates));
    }

    List<FxRevaluation> saved = new ArrayList<>();
    saved.addAll(fxRevaluationRepository.saveAll(reversals));
    saved.addAll(fxRevaluationRepository.saveAll(revaluations));
    return new CloseResult(saved, reversals.size(), revaluations.size(), sum(saved));
  }

  public ReopenResult reversePeriodEntries(UUID tenantId, FinancialPeriod period) {
    Instant now = Instant.now(clock);
    List<FxRevaluation> reversals =
        fxRevaluationRepository.findUnreversedEntriesForPeriod(tenantId, period.getId()).stream()
            .map(entry -> entry.reversal(period.getId(), now))
            .toList();
    List<FxRevaluation> saved = fxRevaluationRepository.saveAll(reversals);
    return new ReopenResult(saved, sum(saved));
  }

  public List<FxRevaluation> getEntries(UUID tenantId, UUID periodId) {
    return fxRevaluationRepository.findByTenantIdAndPeriodIdOrderByCreatedAtAsc(tenantId, periodId);
  }

  public PositionResult getStandingPosition(UUID tenantId, LocalDate asOfDate) {
    List<FxRevaluation> entries =
        fxRevaluationRepository.findPositionEntriesThroughAsOfDate(tenantId, asOfDate);
    return new PositionResult(entries, sum(entries));
  }

  private FxRevaluation revalueInvoice(
      UUID tenantId,
      FinancialPeriod period,
      Invoice invoice,
      String reportingCurrency,
      Instant revaluedAt) {
    snapshotService.ensureSnapshot(tenantId, invoice);

    Money openAmount = invoice.getAmountDue();
    ConvertedMoney closing =
        exchangeRateService.convert(
            tenantId,
            openAmount.getAmount(),
            openAmount.getCurrency().getCurrencyCode(),
            reportingCurrency,
            period.getEndDate());

    InvoiceSide side = sideResolver.resolveSide(tenantId, invoice);
    BigDecimal sideSign =
        side == InvoiceSide.ACCOUNTS_RECEIVABLE ? BigDecimal.ONE : BigDecimal.ONE.negate();
    BigDecimal unrealizedGainLoss =
        openAmount
            .getAmount()
            .multiply(closing.getExchangeRate().subtract(invoice.getIssueExchangeRate()))
            .multiply(sideSign)
            .setScale(REPORTING_AMOUNT_SCALE, RoundingMode.HALF_UP);

    FxRevaluation revaluation =
        FxRevaluation.builder()
            .periodId(period.getId())
            .invoiceId(invoice.getId())
            .entryType(FxRevaluationEntryType.REVALUATION)
            .invoiceSide(side.name())
            .asOfDate(period.getEndDate())
            .openDocumentAmount(openAmount)
            .reportingCurrency(reportingCurrency)
            .issueExchangeRate(invoice.getIssueExchangeRate())
            .issueExchangeRateDate(invoice.getIssueExchangeRateDate())
            .closingExchangeRate(closing.getExchangeRate())
            .closingExchangeRateDate(closing.getRateDate())
            .unrealizedGainLoss(unrealizedGainLoss)
            .revaluedAt(revaluedAt)
            .build();
    revaluation.setTenantId(tenantId);
    return revaluation;
  }

  private BigDecimal sum(List<FxRevaluation> entries) {
    return entries.stream()
        .map(FxRevaluation::getUnrealizedGainLoss)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(REPORTING_AMOUNT_SCALE, RoundingMode.HALF_UP);
  }

  public record CloseResult(
      List<FxRevaluation> entries,
      int reversedEntryCount,
      int revaluationEntryCount,
      BigDecimal periodMovement) {}

  public record ReopenResult(List<FxRevaluation> entries, BigDecimal reversalMovement) {}

  public record PositionResult(List<FxRevaluation> entries, BigDecimal position) {}
}
