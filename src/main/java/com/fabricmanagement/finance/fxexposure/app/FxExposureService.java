package com.fabricmanagement.finance.fxexposure.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.finance.common.app.OpenInvoiceAmountService;
import com.fabricmanagement.finance.common.app.OpenInvoiceAmountService.OpenAmountResult;
import com.fabricmanagement.finance.common.dto.FinanceWarningDto;
import com.fabricmanagement.finance.fx.domain.FxRealization;
import com.fabricmanagement.finance.fx.domain.FxRevaluation;
import com.fabricmanagement.finance.fx.infra.repository.FxRealizationRepository;
import com.fabricmanagement.finance.fx.infra.repository.FxRevaluationRepository;
import com.fabricmanagement.finance.fxexposure.dto.FxExposureCurrencyDto;
import com.fabricmanagement.finance.fxexposure.dto.FxExposureSummaryDto;
import com.fabricmanagement.finance.fxexposure.dto.RealizedFxCurrencyDto;
import com.fabricmanagement.finance.fxexposure.dto.UnrealizedFxCurrencyDto;
import com.fabricmanagement.finance.invoice.app.InvoiceSide;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FxExposureService {

  private static final int REPORTING_SCALE = 4;
  private static final List<InvoiceType> AR_TYPES =
      List.of(InvoiceType.SALES, InvoiceType.DEBIT_NOTE);
  private static final List<InvoiceType> AP_TYPES = List.of(InvoiceType.PURCHASE);
  private static final List<InvoiceStatus> EXCLUDED_OPEN_STATUSES =
      List.of(InvoiceStatus.CANCELLED, InvoiceStatus.VOIDED, InvoiceStatus.DRAFT);

  private final InvoiceRepository invoiceRepository;
  private final FxRealizationRepository fxRealizationRepository;
  private final FxRevaluationRepository fxRevaluationRepository;
  private final TenantReportingCurrencyPort reportingCurrencyPort;
  private final OpenInvoiceAmountService openInvoiceAmountService;
  private final Clock clock;

  public FxExposureSummaryDto getSummary() {
    UUID tenantId = TenantContext.requireTenantId();
    LocalDate asOfDate = LocalDate.now(clock);
    String reportingCurrency = reportingCurrencyPort.getReportingCurrency(tenantId);
    List<FinanceWarningDto> warnings = new ArrayList<>();

    // 1. Open Exposure
    Map<String, ExposureAccumulator> exposureByCurrency = new HashMap<>();

    // AR
    List<Invoice> openAr =
        invoiceRepository.findOpenAccountsReceivable(
            tenantId, AR_TYPES, InvoiceType.CREDIT_NOTE, EXCLUDED_OPEN_STATUSES);
    for (Invoice invoice : openAr) {
      if (!invoice.getCurrency().equalsIgnoreCase(reportingCurrency)) {
        OpenAmountResult openAmount =
            openInvoiceAmountService.signedOpenAmountForSide(
                tenantId, invoice, InvoiceSide.ACCOUNTS_RECEIVABLE, reportingCurrency, asOfDate);
        warnings.addAll(openAmount.warnings());
        exposureByCurrency
            .computeIfAbsent(invoice.getCurrency(), k -> new ExposureAccumulator())
            .addAr(openAmount.signedDocumentAmount(), openAmount.signedReportingAmount());
      }
    }

    // AP
    List<Invoice> openAp =
        invoiceRepository.findOpenAccountsPayable(
            tenantId, AP_TYPES, InvoiceType.CREDIT_NOTE, EXCLUDED_OPEN_STATUSES);
    for (Invoice invoice : openAp) {
      if (!invoice.getCurrency().equalsIgnoreCase(reportingCurrency)) {
        OpenAmountResult openAmount =
            openInvoiceAmountService.signedOpenAmountForSide(
                tenantId, invoice, InvoiceSide.ACCOUNTS_PAYABLE, reportingCurrency, asOfDate);
        warnings.addAll(openAmount.warnings());
        exposureByCurrency
            .computeIfAbsent(invoice.getCurrency(), k -> new ExposureAccumulator())
            .addAp(openAmount.signedDocumentAmount(), openAmount.signedReportingAmount());
      }
    }

    List<FxExposureCurrencyDto> openExposure = new ArrayList<>();
    BigDecimal totalNetReportingExposure = BigDecimal.ZERO;
    for (Map.Entry<String, ExposureAccumulator> entry : exposureByCurrency.entrySet()) {
      ExposureAccumulator acc = entry.getValue();
      BigDecimal netDoc =
          acc.arDoc.subtract(acc.apDoc).setScale(REPORTING_SCALE, RoundingMode.HALF_UP);
      BigDecimal netRep =
          acc.arRep.subtract(acc.apRep).setScale(REPORTING_SCALE, RoundingMode.HALF_UP);
      openExposure.add(
          new FxExposureCurrencyDto(
              entry.getKey(),
              acc.arDoc.setScale(REPORTING_SCALE, RoundingMode.HALF_UP),
              acc.apDoc.setScale(REPORTING_SCALE, RoundingMode.HALF_UP),
              netDoc,
              netRep));
      totalNetReportingExposure = totalNetReportingExposure.add(netRep);
    }
    openExposure.sort((a, b) -> a.currency().compareTo(b.currency()));

    // 2. Realized FX
    Instant now = clock.instant();
    Instant fromDate = now.minus(90, ChronoUnit.DAYS);
    List<FxRealization> realizations =
        fxRealizationRepository.findByTenantIdAndRealizedAtBetween(tenantId, fromDate, now);

    Map<String, BigDecimal> realizedByCurrency = new HashMap<>();
    BigDecimal totalRealized = BigDecimal.ZERO;
    for (FxRealization r : realizations) {
      String currency = r.getDocumentAmount().getCurrency().getCurrencyCode();
      if (!currency.equalsIgnoreCase(reportingCurrency)) {
        realizedByCurrency.merge(currency, r.getRealizedGainLoss(), BigDecimal::add);
        totalRealized = totalRealized.add(r.getRealizedGainLoss());
      }
    }

    List<RealizedFxCurrencyDto> realizedFx =
        realizedByCurrency.entrySet().stream()
            .map(
                e ->
                    new RealizedFxCurrencyDto(
                        e.getKey(), e.getValue().setScale(REPORTING_SCALE, RoundingMode.HALF_UP)))
            .sorted((a, b) -> a.currency().compareTo(b.currency()))
            .toList();

    // 3. Unrealized FX
    List<FxRevaluation> revaluations =
        fxRevaluationRepository.findPositionEntriesThroughAsOfDate(tenantId, asOfDate);

    Map<String, BigDecimal> unrealizedByCurrency = new HashMap<>();
    BigDecimal totalUnrealized = BigDecimal.ZERO;
    LocalDate maxAsOfDate = null;

    for (FxRevaluation r : revaluations) {
      String currency = r.getOpenDocumentAmount().getCurrency().getCurrencyCode();
      if (!currency.equalsIgnoreCase(reportingCurrency)) {
        unrealizedByCurrency.merge(currency, r.getUnrealizedGainLoss(), BigDecimal::add);
        totalUnrealized = totalUnrealized.add(r.getUnrealizedGainLoss());
        if (maxAsOfDate == null || r.getAsOfDate().isAfter(maxAsOfDate)) {
          maxAsOfDate = r.getAsOfDate();
        }
      }
    }

    List<UnrealizedFxCurrencyDto> unrealizedFx =
        unrealizedByCurrency.entrySet().stream()
            .map(
                e ->
                    new UnrealizedFxCurrencyDto(
                        e.getKey(), e.getValue().setScale(REPORTING_SCALE, RoundingMode.HALF_UP)))
            .sorted((a, b) -> a.currency().compareTo(b.currency()))
            .toList();

    return new FxExposureSummaryDto(
        asOfDate,
        reportingCurrency,
        totalNetReportingExposure.setScale(REPORTING_SCALE, RoundingMode.HALF_UP),
        openExposure,
        totalRealized.setScale(REPORTING_SCALE, RoundingMode.HALF_UP),
        realizedFx,
        maxAsOfDate,
        totalUnrealized.setScale(REPORTING_SCALE, RoundingMode.HALF_UP),
        unrealizedFx,
        warnings);
  }

  private static class ExposureAccumulator {
    BigDecimal arDoc = BigDecimal.ZERO;
    BigDecimal arRep = BigDecimal.ZERO;
    BigDecimal apDoc = BigDecimal.ZERO;
    BigDecimal apRep = BigDecimal.ZERO;

    void addAr(BigDecimal doc, BigDecimal rep) {
      arDoc = arDoc.add(doc);
      arRep = arRep.add(rep);
    }

    void addAp(BigDecimal doc, BigDecimal rep) {
      // ap components are inherently liabilities, but we track gross values
      // and subtract later, so we use their absolute values (or handle sign).
      // invoiceService.signedOpenAmountForSide already returns properly signed values?
      // Wait, signedOpenAmountForSide returns positive for normal AR/AP.
      // Credit notes are negative. So we just add them.
      apDoc = apDoc.add(doc);
      apRep = apRep.add(rep);
    }
  }
}
