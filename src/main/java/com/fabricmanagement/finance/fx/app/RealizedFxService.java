package com.fabricmanagement.finance.fx.app;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.finance.common.app.SettlementFxResult;
import com.fabricmanagement.finance.fx.domain.FxRealization;
import com.fabricmanagement.finance.fx.domain.FxRealizationSourceType;
import com.fabricmanagement.finance.fx.infra.repository.FxRealizationRepository;
import com.fabricmanagement.finance.invoice.app.InvoiceReportingSnapshotService;
import com.fabricmanagement.finance.invoice.app.InvoiceSide;
import com.fabricmanagement.finance.invoice.app.InvoiceSideResolver;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RealizedFxService {

  private static final int REPORTING_AMOUNT_SCALE = 4;

  private final FxRealizationRepository fxRealizationRepository;
  private final InvoiceReportingSnapshotService snapshotService;
  private final InvoiceSideResolver sideResolver;
  private final ExchangeRateService exchangeRateService;
  private final Clock clock;

  public SettlementFxResult recordPaymentAllocation(
      UUID tenantId, Invoice invoice, UUID allocationId, Money amount, LocalDate settlementDate) {
    return record(
        tenantId,
        FxRealizationSourceType.PAYMENT_ALLOCATION,
        allocationId,
        invoice,
        amount,
        settlementDate);
  }

  public SettlementFxResult recordCreditNoteApplication(
      UUID tenantId,
      Invoice targetInvoice,
      UUID applicationId,
      Money amount,
      LocalDate appliedDate) {
    return record(
        tenantId,
        FxRealizationSourceType.CREDIT_NOTE_APPLICATION,
        applicationId,
        targetInvoice,
        amount,
        appliedDate);
  }

  public void reversePaymentAllocation(UUID tenantId, UUID allocationId) {
    reverse(tenantId, FxRealizationSourceType.PAYMENT_ALLOCATION, allocationId);
  }

  public void reverseCreditNoteApplication(UUID tenantId, UUID applicationId) {
    reverse(tenantId, FxRealizationSourceType.CREDIT_NOTE_APPLICATION, applicationId);
  }

  @Transactional(readOnly = true)
  public Optional<SettlementFxResult> findResultForSource(
      UUID tenantId, FxRealizationSourceType sourceType, UUID sourceId) {
    return fxRealizationRepository
        .findFirstByTenantIdAndSourceTypeAndSourceIdAndReversalOfIdIsNullOrderByCreatedAtAsc(
            tenantId, sourceType, sourceId)
        .map(
            original -> {
              BigDecimal net =
                  fxRealizationRepository.sumRealizedGainLoss(tenantId, sourceType, sourceId);
              return toResult(original, net);
            });
  }

  private SettlementFxResult record(
      UUID tenantId,
      FxRealizationSourceType sourceType,
      UUID sourceId,
      Invoice invoice,
      Money amount,
      LocalDate settlementDate) {
    snapshotService.ensureSnapshot(tenantId, invoice);

    String reportingCurrency = invoice.getReportingCurrency();
    if (invoice.getCurrency().equalsIgnoreCase(reportingCurrency)) {
      return SettlementFxResult.zero(reportingCurrency, BigDecimal.ONE, settlementDate);
    }

    Optional<SettlementFxResult> existing = findResultForSource(tenantId, sourceType, sourceId);
    if (existing.isPresent()) {
      return existing.get();
    }

    ConvertedMoney settlement =
        exchangeRateService.convert(
            tenantId, amount.getAmount(), invoice.getCurrency(), reportingCurrency, settlementDate);

    InvoiceSide side = sideResolver.resolveSide(tenantId, invoice);
    BigDecimal realized =
        amount
            .getAmount()
            .multiply(settlement.getExchangeRate().subtract(invoice.getIssueExchangeRate()))
            .multiply(BigDecimal.valueOf(side.realizedFxSign()))
            .setScale(REPORTING_AMOUNT_SCALE, RoundingMode.HALF_UP);

    FxRealization fxRealization =
        FxRealization.builder()
            .sourceType(sourceType)
            .sourceId(sourceId)
            .invoiceId(invoice.getId())
            .documentAmount(amount)
            .reportingCurrency(reportingCurrency)
            .issueExchangeRate(invoice.getIssueExchangeRate())
            .issueExchangeRateDate(invoice.getIssueExchangeRateDate())
            .settlementExchangeRate(settlement.getExchangeRate())
            .settlementExchangeRateDate(settlement.getRateDate())
            .realizedGainLoss(realized)
            .realizedAt(Instant.now(clock))
            .build();
    fxRealization.setTenantId(tenantId);

    FxRealization saved = fxRealizationRepository.save(fxRealization);
    return toResult(saved, saved.getRealizedGainLoss());
  }

  private void reverse(UUID tenantId, FxRealizationSourceType sourceType, UUID sourceId) {
    fxRealizationRepository
        .findByTenantIdAndSourceTypeAndSourceIdAndReversalOfIdIsNull(tenantId, sourceType, sourceId)
        .stream()
        .filter(
            original ->
                !fxRealizationRepository.existsByTenantIdAndReversalOfId(
                    tenantId, original.getId()))
        .map(original -> original.reversal(Instant.now(clock)))
        .forEach(fxRealizationRepository::save);
  }

  private SettlementFxResult toResult(FxRealization fxRealization, BigDecimal realizedGainLoss) {
    return new SettlementFxResult(
        fxRealization.getReportingCurrency(),
        realizedGainLoss,
        fxRealization.getSettlementExchangeRate(),
        fxRealization.getSettlementExchangeRateDate());
  }
}
