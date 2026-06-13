package com.fabricmanagement.costing.app;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.app.port.TenantCostingSettingsPort;
import com.fabricmanagement.costing.app.port.WorkOrderPlanningUpdatePort;
import com.fabricmanagement.costing.domain.calculation.*;
import com.fabricmanagement.costing.domain.event.CostVarianceDetectedEvent;
import com.fabricmanagement.costing.domain.exception.CostingDomainException;
import com.fabricmanagement.costing.domain.exception.PriceListNotFoundException;
import com.fabricmanagement.costing.domain.item.CalculationBase;
import com.fabricmanagement.costing.domain.item.CostItem;
import com.fabricmanagement.costing.domain.price.PriceList;
import com.fabricmanagement.costing.domain.price.PriceListItem;
import com.fabricmanagement.costing.domain.template.CostTemplate;
import com.fabricmanagement.costing.domain.template.CostTemplateItem;
import com.fabricmanagement.costing.dto.WorkOrderCostReportResponse;
import com.fabricmanagement.costing.infra.repository.*;
import com.fabricmanagement.production.execution.workorder.app.port.ConsumptionCostInput;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core orchestration service for the 3-stage costing pipeline: ESTIMATED → PLANNED → ACTUAL.
 *
 * <p>Each stage call is idempotent for the (entityType, entityId) pair — calling it again
 * re-calculates and overwrites the existing record for that stage.
 *
 * <p>Variance detection: when a new stage total deviates from the previous stage total by more than
 * the tenant-configured variance threshold (see {@link TenantCostingSettingsPort}), a {@link
 * CostVarianceDetectedEvent} is published.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CostCalculationService {

  private final CostItemRepository costItemRepo;
  private final CostTemplateRepository costTemplateRepo;
  private final PriceListRepository priceListRepo;
  private final PriceListItemRepository priceListItemRepo;
  private final CostCalculationRepository costCalcRepo;
  private final TenantCostingSettingsPort tenantCostingSettingsPort;
  private final ApplicationEventPublisher eventPublisher;
  private final Optional<WorkOrderPlanningUpdatePort> workOrderPlanningUpdatePort;
  private final ExchangeRateService exchangeRateService;
  private final TenantReportingCurrencyPort tenantReportingCurrencyPort;

  private String getTargetCurrency(UUID tenantId) {
    return tenantReportingCurrencyPort.getReportingCurrency(tenantId);
  }

  // ============================================================
  // PUBLIC API
  // ============================================================

  /**
   * Compute or re-compute the ESTIMATED cost (Quote stage).
   *
   * @param tenantId owning tenant
   * @param quoteId the Quote entity ID
   * @param moduleType e.g. "FIBER"
   * @param productId the primary product being quoted
   * @param totalQuantityKg the projected order quantity
   * @param tradingPartnerId the target customer/supplier (for volume lookups)
   * @return the saved CostCalculation
   */
  @Transactional
  public CostCalculation computeEstimated(
      UUID tenantId,
      UUID quoteId,
      String moduleType,
      UUID productId,
      BigDecimal totalQuantityKg,
      UUID tradingPartnerId) {
    return computeEstimated(
        tenantId,
        quoteId,
        moduleType,
        productId,
        totalQuantityKg,
        tradingPartnerId,
        LocalDate.now());
  }

  /** Compute or re-compute the ESTIMATED cost (Quote stage) with a specific rate date. */
  @Transactional
  public CostCalculation computeEstimated(
      UUID tenantId,
      UUID quoteId,
      String moduleType,
      UUID productId,
      BigDecimal totalQuantityKg,
      UUID tradingPartnerId,
      LocalDate rateDate) {
    return compute(
        tenantId,
        CostEntityType.QUOTE,
        quoteId,
        moduleType,
        CostStage.ESTIMATED,
        productId,
        totalQuantityKg,
        tradingPartnerId,
        rateDate);
  }

  /**
   * Compute or re-compute the PLANNED cost (WorkOrder confirmed stage).
   *
   * @param tenantId owning tenant
   * @param workOrderId the WorkOrder entity ID
   * @param moduleType production module
   * @param productId primary product
   * @param plannedQuantityKg the work order planned quantity
   * @param supplierId the selected supplier (for contracted prices)
   * @return the saved CostCalculation
   */
  @Transactional
  public CostCalculation computePlanned(
      UUID tenantId,
      UUID workOrderId,
      String moduleType,
      UUID productId,
      BigDecimal plannedQuantityKg,
      UUID supplierId) {
    return computePlanned(
        tenantId,
        workOrderId,
        moduleType,
        productId,
        plannedQuantityKg,
        supplierId,
        LocalDate.now());
  }

  /**
   * Compute or re-compute the PLANNED cost (WorkOrder confirmed stage) with a specific rate date.
   */
  @Transactional
  public CostCalculation computePlanned(
      UUID tenantId,
      UUID workOrderId,
      String moduleType,
      UUID productId,
      BigDecimal plannedQuantityKg,
      UUID supplierId,
      LocalDate rateDate) {
    CostCalculation calculation =
        compute(
            tenantId,
            CostEntityType.WORK_ORDER,
            workOrderId,
            moduleType,
            CostStage.PLANNED,
            productId,
            plannedQuantityKg,
            supplierId,
            rateDate);

    workOrderPlanningUpdatePort.ifPresent(
        port ->
            port.updatePlannedCost(
                tenantId, workOrderId, calculation.getTotalCost(), calculation.getCurrency()));

    return calculation;
  }

  /**
   * Compute or re-compute the ACTUAL cost (Batch completed stage).
   *
   * @param tenantId owning tenant
   * @param batchId the Batch entity ID
   * @param moduleType production module
   * @param productId the batch product
   * @param actualQuantityKg actual quantity produced (net of waste)
   * @param supplierId the supplier used
   * @return the saved CostCalculation
   */
  @Transactional
  public CostCalculation computeActual(
      UUID tenantId,
      UUID batchId,
      String moduleType,
      UUID productId,
      BigDecimal actualQuantityKg,
      UUID supplierId) {
    return computeActual(
        tenantId, batchId, moduleType, productId, actualQuantityKg, supplierId, LocalDate.now());
  }

  /** Compute or re-compute the ACTUAL cost (Batch completed stage) with a specific rate date. */
  @Transactional
  public CostCalculation computeActual(
      UUID tenantId,
      UUID batchId,
      String moduleType,
      UUID productId,
      BigDecimal actualQuantityKg,
      UUID supplierId,
      LocalDate rateDate) {
    return compute(
        tenantId,
        CostEntityType.BATCH,
        batchId,
        moduleType,
        CostStage.ACTUAL,
        productId,
        actualQuantityKg,
        supplierId,
        rateDate);
  }

  /**
   * Sprint 6: Compute ACTUAL cost for a WorkOrder using per-consumption product prices.
   *
   * <p>For RAW_PRODUCT template items: iterates over each ConsumptionCostInput and resolves the
   * per-product unit price independently. This gives accurate blending cost — e.g. 60% FIBER_A at
   * 45 TRY/kg + 40% FIBER_B at 52 TRY/kg instead of pricing by the output yarn.
   *
   * <p>For all other template items (LABOR, MACHINE, OVERHEAD, etc.): standard computation against
   * outputProductId + actualOutputQty, unchanged from Sprint 5.
   *
   * <p>Sprint 7b: Each line's cost is automatically converted from the price list currency to the
   * tenant's reporting currency via {@link ExchangeRateService}. PERCENTAGE and FIXED items are
   * exempt from conversion (their totals are already in the reporting currency).
   */
  @Transactional
  public CostCalculation computeActualForWorkOrderWithConsumptions(
      UUID tenantId,
      UUID workOrderId,
      String outputModuleType,
      UUID outputProductId,
      BigDecimal actualOutputQty,
      UUID tradingPartnerId,
      List<ConsumptionCostInput> consumptions) {
    return computeActualForWorkOrderWithConsumptions(
        tenantId,
        workOrderId,
        outputModuleType,
        outputProductId,
        actualOutputQty,
        tradingPartnerId,
        consumptions,
        LocalDate.now());
  }

  /**
   * Compute ACTUAL cost for a WorkOrder using per-consumption product prices, explicitly defining
   * the rate calculation date.
   */
  @Transactional
  public CostCalculation computeActualForWorkOrderWithConsumptions(
      UUID tenantId,
      UUID workOrderId,
      String outputModuleType,
      UUID outputProductId,
      BigDecimal actualOutputQty,
      UUID tradingPartnerId,
      List<ConsumptionCostInput> consumptions,
      LocalDate rateDate) {

    // 1. Idempotent recalculation — soft-delete any existing WORK_ORDER/ACTUAL record
    costCalcRepo
        .findActiveByEntityTypeAndEntityIdAndStage(
            CostEntityType.WORK_ORDER, workOrderId, CostStage.ACTUAL)
        .ifPresent(
            existing -> {
              existing.delete();
              costCalcRepo.save(existing);
            });

    // PriceList per moduleType — single DB query per unique moduleType (Cache)
    Map<String, Optional<PriceList>> priceListCache =
        consumptions.stream()
            .map(c -> c.moduleType().name())
            .distinct()
            .collect(
                Collectors.toMap(
                    mt -> mt, mt -> priceListRepo.findActiveForModule(tenantId, mt, rateDate)));

    // 2. Resolve PriceList and Template for the output module (used for LABOR, OVERHEAD, etc.)
    PriceList outputPriceList =
        priceListRepo
            .findActiveForModule(tenantId, outputModuleType, rateDate)
            .orElseThrow(() -> new PriceListNotFoundException(outputModuleType));

    CostTemplate template =
        costTemplateRepo
            .findDefault(tenantId, outputModuleType)
            .orElseThrow(
                () ->
                    new CostingDomainException(
                        "No default cost template for module: " + outputModuleType));

    String targetCurrency = getTargetCurrency(tenantId);

    // 3. Create calculation shell
    CostCalculation calc =
        CostCalculation.create(
            tenantId,
            CostEntityType.WORK_ORDER,
            workOrderId,
            outputModuleType,
            CostStage.ACTUAL,
            targetCurrency);
    calc.setCostTemplateId(template.getId());

    List<CostItem> relevantItems = costItemRepo.findActiveForModule(outputModuleType);

    // Pass 1: Quantity-based (PER_KG, PER_HOUR, PER_UNIT) and FIXED items
    for (CostTemplateItem templateItem : template.getItems()) {
      if (!templateItem.isIncluded()) continue;

      Optional<CostItem> costItemOpt =
          relevantItems.stream()
              .filter(ci -> ci.getCode().equals(templateItem.costItemCode()))
              .findFirst();
      if (costItemOpt.isEmpty()) {
        log.warn("CostItem '{}' in template not found — skipping", templateItem.costItemCode());
        calc.recordMissing(templateItem.costItemCode(), null, "CostItem not found for module");
        continue;
      }
      CostItem costItem = costItemOpt.get();

      if (costItem.getCalculationBase() == CalculationBase.PERCENTAGE) {
        continue; // Processed in Pass 2
      }

      if ("RAW_PRODUCT".equals(costItem.getCode())) {
        // ── Multi-product path: one line per consumption record ──
        for (ConsumptionCostInput consumption : consumptions) {

          PriceList consumptionPriceList =
              priceListCache
                  .getOrDefault(consumption.moduleType().name(), Optional.empty())
                  .orElse(null);

          if (consumptionPriceList == null) {
            log.warn(
                "No active price list for module '{}' — raw product cost skipped for productId {}",
                consumption.moduleType().name(),
                consumption.productId());
            calc.recordMissing(
                "RAW_PRODUCT",
                consumption.productId(),
                "No active price list for module " + consumption.moduleType().name());
            continue;
          }

          Optional<PriceListItem> priceItemOpt =
              priceListItemRepo.findBest(
                  consumptionPriceList.getId(),
                  "RAW_PRODUCT",
                  consumption.productId(),
                  tradingPartnerId);
          if (priceItemOpt.isEmpty()) {
            log.debug(
                "No RAW_PRODUCT price for productId {} — skipping line", consumption.productId());
            calc.recordMissing(
                "RAW_PRODUCT", consumption.productId(), "No RAW_PRODUCT price for product");
            continue;
          }

          PriceListItem priceItem = priceItemOpt.get();
          BigDecimal unitPrice = priceItem.resolveUnitPrice(consumption.consumedWeight());
          BigDecimal lineTotal =
              unitPrice
                  .multiply(consumption.consumedWeight())
                  .setScale(4, java.math.RoundingMode.HALF_UP);

          CostCalculationLine line = new CostCalculationLine();
          line.setTenantId(tenantId);
          line.setCostItemCode("RAW_PRODUCT");
          line.setQty(consumption.consumedWeight());
          line.setUnit(consumption.unit());
          line.setUnitPrice(unitPrice);
          line.setCurrency(priceItem.getCurrency());
          line.setVolumeDiscountApplied(!unitPrice.equals(priceItem.getUnitPrice()));
          line.setProductId(consumption.productId());

          ConvertedMoney convertedTotal =
              exchangeRateService.convert(
                  lineTotal, priceItem.getCurrency(), targetCurrency, rateDate);
          line.setConvertedTotal(convertedTotal);
          line.setTotalInBaseCurrency(convertedTotal.getConvertedAmount());

          calc.addLine(line);
        }
      } else {
        // ── Standard path: output product + output qty (LABOR, MACHINE, OVERHEAD vb.) ──
        Optional<PriceListItem> priceItemOpt =
            priceListItemRepo.findBest(
                outputPriceList.getId(), costItem.getCode(), outputProductId, tradingPartnerId);
        if (priceItemOpt.isEmpty()) {
          log.debug("No price for '{}' in output price list — skipping", costItem.getCode());
          calc.recordMissing(costItem.getCode(), null, "No price found in price list");
          continue;
        }

        PriceListItem priceItem = priceItemOpt.get();
        BigDecimal effectiveUnitPrice = priceItem.resolveUnitPrice(actualOutputQty);
        boolean volumeDiscountApplied = !effectiveUnitPrice.equals(priceItem.getUnitPrice());

        // For Pass 1 items, currentTotal is irrelevant except as a dummy.
        BigDecimal lineTotal =
            computeLineTotal(
                costItem,
                effectiveUnitPrice,
                actualOutputQty,
                templateItem.weight(),
                BigDecimal.ZERO);

        String unit =
            switch (costItem.getCalculationBase()) {
              case PER_KG -> "KG";
              case PER_HOUR -> "HOUR";
              case PER_UNIT -> "UNIT";
              case PERCENTAGE, FIXED -> null;
            };

        CostCalculationLine line = new CostCalculationLine();
        line.setTenantId(tenantId);
        line.setCostItemCode(costItem.getCode());
        line.setQty(actualOutputQty);
        line.setUnit(unit);
        line.setUnitPrice(effectiveUnitPrice);
        line.setCurrency(priceItem.getCurrency());
        line.setVolumeDiscountApplied(volumeDiscountApplied);

        // PERCENTAGE/FIXED: lineTotal derived from running total (already targetCurrency)
        ConvertedMoney convertedTotal;
        if (costItem.getCalculationBase() == CalculationBase.FIXED) {
          convertedTotal = ConvertedMoney.sameUnit(lineTotal, targetCurrency);
        } else {
          convertedTotal =
              exchangeRateService.convert(
                  lineTotal, priceItem.getCurrency(), targetCurrency, rateDate);
        }
        line.setConvertedTotal(convertedTotal);
        line.setTotalInBaseCurrency(convertedTotal.getConvertedAmount());

        calc.addLine(line);
      }
    }

    // Pass 2: PERCENTAGE items
    BigDecimal pass1Total = calc.getTotalCost(); // Deterministic base for percentages

    for (CostTemplateItem templateItem : template.getItems()) {
      if (!templateItem.isIncluded()) continue;

      Optional<CostItem> costItemOpt =
          relevantItems.stream()
              .filter(ci -> ci.getCode().equals(templateItem.costItemCode()))
              .findFirst();
      if (costItemOpt.isEmpty()) continue; // Already logged in Pass 1

      CostItem costItem = costItemOpt.get();
      if (costItem.getCalculationBase() != CalculationBase.PERCENTAGE) {
        continue; // Handled in Pass 1
      }

      // Percentage items only apply to the standard path (never RAW_PRODUCT blending)
      Optional<PriceListItem> priceItemOpt =
          priceListItemRepo.findBest(
              outputPriceList.getId(), costItem.getCode(), outputProductId, tradingPartnerId);
      if (priceItemOpt.isEmpty()) {
        log.debug(
            "No percentage rate for '{}' in output price list — skipping", costItem.getCode());
        calc.recordMissing(costItem.getCode(), null, "No price found in price list");
        continue;
      }

      PriceListItem priceItem = priceItemOpt.get();
      BigDecimal effectiveUnitPrice = priceItem.resolveUnitPrice(actualOutputQty);
      boolean volumeDiscountApplied = !effectiveUnitPrice.equals(priceItem.getUnitPrice());

      // Line total uses pass1Total as its base
      BigDecimal lineTotal =
          computeLineTotal(
              costItem, effectiveUnitPrice, actualOutputQty, templateItem.weight(), pass1Total);

      CostCalculationLine line = new CostCalculationLine();
      line.setTenantId(tenantId);
      line.setCostItemCode(costItem.getCode());
      line.setQty(actualOutputQty);
      line.setUnit(null);
      line.setUnitPrice(effectiveUnitPrice);
      line.setCurrency(priceItem.getCurrency());
      line.setVolumeDiscountApplied(volumeDiscountApplied);

      // PERCENTAGE is already in targetCurrency because it was calculated from pass1Total
      ConvertedMoney convertedTotal = ConvertedMoney.sameUnit(lineTotal, targetCurrency);
      line.setConvertedTotal(convertedTotal);
      line.setTotalInBaseCurrency(convertedTotal.getConvertedAmount());

      calc.addLine(line);
    }

    var saved = costCalcRepo.save(calc);
    log.info(
        "Multi-product CostCalculation saved: workOrderId={} stage=ACTUAL totalCost={}",
        workOrderId,
        saved.getTotalCost());

    detectAndPublishVariance(tenantId, saved, CostEntityType.WORK_ORDER, workOrderId);
    return saved;
  }

  /**
   * Retrieves the PLANNED and ACTUAL cost calculations for a WorkOrder and builds a structured
   * report with per-product breakdown and variance summary.
   *
   * <p>Either stage may be absent (not yet calculated or calculation failed) — the response handles
   * partial data gracefully.
   *
   * @param tenantId the current tenant
   * @param workOrderId the WorkOrder to report on
   * @return WorkOrderCostReportResponse (never null, but may have null sections)
   */
  @Transactional(readOnly = true)
  public WorkOrderCostReportResponse getWorkOrderCostReport(UUID tenantId, UUID workOrderId) {
    Optional<CostCalculation> planned =
        costCalcRepo.findActiveByEntityTypeAndEntityIdAndStage(
            CostEntityType.WORK_ORDER, workOrderId, CostStage.PLANNED);

    Optional<CostCalculation> actual =
        costCalcRepo.findActiveByEntityTypeAndEntityIdAndStage(
            CostEntityType.WORK_ORDER, workOrderId, CostStage.ACTUAL);

    // Force-load lazy lines within this transaction
    planned.ifPresent(c -> c.getLines().size());
    actual.ifPresent(c -> c.getLines().size());

    return WorkOrderCostReportResponse.of(workOrderId, planned, actual);
  }

  // ============================================================
  // INTERNAL ENGINE
  // ============================================================

  private CostCalculation compute(
      UUID tenantId,
      CostEntityType entityType,
      UUID entityId,
      String moduleType,
      CostStage stage,
      UUID productId,
      BigDecimal quantityKg,
      UUID tradingPartnerId,
      LocalDate rateDate) {

    // 1. Soft-delete existing calculation for this entity+stage (idempotent recalculation).
    //    Hard-delete is risky: if save() fails after deleteById(), the entity is lost with no
    //    rollback safety net. Soft-delete keeps the audit trail intact.
    costCalcRepo
        .findActiveByEntityTypeAndEntityIdAndStage(entityType, entityId, stage)
        .ifPresent(
            existing -> {
              existing.delete(); // BaseEntity.delete() → sets deletedAt + isActive=false
              costCalcRepo.save(existing);
            });

    // 2. Resolve price list
    PriceList priceList =
        priceListRepo
            .findActiveForModule(tenantId, moduleType, rateDate)
            .orElseThrow(() -> new PriceListNotFoundException(moduleType));

    // 3. Resolve cost template
    CostTemplate template =
        costTemplateRepo
            .findDefault(tenantId, moduleType)
            .orElseThrow(
                () ->
                    new CostingDomainException(
                        "No default cost template configured for module: "
                            + moduleType
                            + " (tenantId="
                            + tenantId
                            + ")"));

    String targetCurrency = getTargetCurrency(tenantId);

    // 4. Build calculation
    var calc =
        CostCalculation.create(tenantId, entityType, entityId, moduleType, stage, targetCurrency);
    calc.setCostTemplateId(template.getId());

    List<CostItem> relevantItems = costItemRepo.findActiveForModule(moduleType);

    // Pass 1: Quantity-based (PER_KG, PER_HOUR, PER_UNIT) and FIXED items
    for (CostTemplateItem templateItem : template.getItems()) {
      if (!templateItem.isIncluded()) continue;

      Optional<CostItem> costItemOpt =
          relevantItems.stream()
              .filter(ci -> ci.getCode().equals(templateItem.costItemCode()))
              .findFirst();
      if (costItemOpt.isEmpty()) {
        log.warn("CostItem '{}' in template not found — skipping", templateItem.costItemCode());
        calc.recordMissing(templateItem.costItemCode(), null, "CostItem not found for module");
        continue;
      }

      CostItem costItem = costItemOpt.get();
      if (costItem.getCalculationBase() == CalculationBase.PERCENTAGE) {
        continue; // Processed in Pass 2
      }

      Optional<PriceListItem> priceItemOpt =
          priceListItemRepo.findBest(
              priceList.getId(), costItem.getCode(), productId, tradingPartnerId);

      if (priceItemOpt.isEmpty()) {
        log.debug(
            "No price found for cost item '{}' in price list {} — skipping",
            costItem.getCode(),
            priceList.getId());
        calc.recordMissing(costItem.getCode(), null, "No price found in price list");
        continue;
      }

      PriceListItem priceItem = priceItemOpt.get();
      BigDecimal effectiveUnitPrice = priceItem.resolveUnitPrice(quantityKg);
      boolean volumeDiscountApplied = !effectiveUnitPrice.equals(priceItem.getUnitPrice());

      // Calculate line total. For Pass 1 items, currentTotal is irrelevant except as a dummy.
      BigDecimal lineTotal =
          computeLineTotal(
              costItem, effectiveUnitPrice, quantityKg, templateItem.weight(), BigDecimal.ZERO);

      // Unit is derived from CalculationBase — not hardcoded
      String unit =
          switch (costItem.getCalculationBase()) {
            case PER_KG -> "KG";
            case PER_HOUR -> "HOUR";
            case PER_UNIT -> "UNIT";
            // PERCENTAGE and FIXED lines have no meaningful quantity unit
            case PERCENTAGE, FIXED -> null;
          };

      CostCalculationLine line = new CostCalculationLine();
      line.setTenantId(tenantId);
      line.setCostItemCode(costItem.getCode());
      line.setQty(quantityKg);
      line.setUnit(unit);
      line.setUnitPrice(effectiveUnitPrice);
      line.setCurrency(priceItem.getCurrency());
      line.setVolumeDiscountApplied(volumeDiscountApplied);

      ConvertedMoney convertedTotal;
      if (costItem.getCalculationBase() == CalculationBase.FIXED) {
        convertedTotal = ConvertedMoney.sameUnit(lineTotal, targetCurrency);
      } else {
        convertedTotal =
            exchangeRateService.convert(
                lineTotal, priceItem.getCurrency(), targetCurrency, rateDate);
      }
      line.setConvertedTotal(convertedTotal);
      line.setTotalInBaseCurrency(convertedTotal.getConvertedAmount());

      calc.addLine(line);
    }

    // Pass 2: PERCENTAGE items
    BigDecimal pass1Total = calc.getTotalCost(); // Deterministic base for percentages

    for (CostTemplateItem templateItem : template.getItems()) {
      if (!templateItem.isIncluded()) continue;

      Optional<CostItem> costItemOpt =
          relevantItems.stream()
              .filter(ci -> ci.getCode().equals(templateItem.costItemCode()))
              .findFirst();
      if (costItemOpt.isEmpty()) continue; // Already logged in Pass 1

      CostItem costItem = costItemOpt.get();
      if (costItem.getCalculationBase() != CalculationBase.PERCENTAGE) {
        continue; // Handled in Pass 1
      }

      Optional<PriceListItem> priceItemOpt =
          priceListItemRepo.findBest(
              priceList.getId(), costItem.getCode(), productId, tradingPartnerId);

      if (priceItemOpt.isEmpty()) {
        log.debug(
            "No percentage rate found for cost item '{}' in price list {} — skipping",
            costItem.getCode(),
            priceList.getId());
        calc.recordMissing(costItem.getCode(), null, "No price found in price list");
        continue;
      }

      PriceListItem priceItem = priceItemOpt.get();
      BigDecimal effectiveUnitPrice = priceItem.resolveUnitPrice(quantityKg);
      boolean volumeDiscountApplied = !effectiveUnitPrice.equals(priceItem.getUnitPrice());

      // Line total uses pass1Total as its base
      BigDecimal lineTotal =
          computeLineTotal(
              costItem, effectiveUnitPrice, quantityKg, templateItem.weight(), pass1Total);

      CostCalculationLine line = new CostCalculationLine();
      line.setTenantId(tenantId);
      line.setCostItemCode(costItem.getCode());
      line.setQty(quantityKg);
      line.setUnit(null);
      line.setUnitPrice(effectiveUnitPrice);
      line.setCurrency(priceItem.getCurrency());
      line.setVolumeDiscountApplied(volumeDiscountApplied);

      // PERCENTAGE is already in targetCurrency because it was calculated from pass1Total
      ConvertedMoney convertedTotal = ConvertedMoney.sameUnit(lineTotal, targetCurrency);
      line.setConvertedTotal(convertedTotal);
      line.setTotalInBaseCurrency(convertedTotal.getConvertedAmount());

      calc.addLine(line);
    }

    var saved = costCalcRepo.save(calc);
    log.info(
        "CostCalculation saved: entityType={} entityId={} stage={} totalCost={}",
        entityType,
        entityId,
        stage,
        saved.getTotalCost());

    // 5. Variance detection
    detectAndPublishVariance(tenantId, saved, entityType, entityId);

    return saved;
  }

  /**
   * Two-pass computation:
   *
   * <ol>
   *   <li>Pass 1 — quantity-based (PER_KG, PER_HOUR, PER_UNIT) and FIXED items
   *   <li>Pass 2 — PERCENTAGE items; base = sum of all pass-1 line totals
   * </ol>
   *
   * <p>The percentage base <b>includes fixed costs</b> — i.e. overhead is computed on top of both
   * variable and fixed production costs. This is an intentional business decision: a 12% overhead
   * on a 1000 TRY batch includes the 50 TRY packaging (FIXED) in its base.
   *
   * <p>PERCENTAGE-on-PERCENTAGE is not supported. All PERCENTAGE items compute against the same
   * deterministic base (pass-1 total), making the result independent of template item order.
   */
  private BigDecimal computeLineTotal(
      CostItem item,
      BigDecimal unitPrice,
      BigDecimal quantity,
      BigDecimal templateWeight,
      BigDecimal currentTotal) {
    return switch (item.getCalculationBase()) {
      case PER_KG, PER_HOUR, PER_UNIT -> {
        BigDecimal base = unitPrice.multiply(quantity);
        yield templateWeight != null
            ? base.multiply(templateWeight).setScale(4, RoundingMode.HALF_UP)
            : base.setScale(4, RoundingMode.HALF_UP);
      }
      case PERCENTAGE -> {
        // Percentage = rate × current running total (overhead applied to what came before)
        BigDecimal rate = templateWeight != null ? templateWeight : unitPrice;
        yield currentTotal.multiply(rate).setScale(4, RoundingMode.HALF_UP);
      }
      case FIXED -> unitPrice.setScale(4, RoundingMode.HALF_UP);
    };
  }

  private void detectAndPublishVariance(
      UUID tenantId, CostCalculation current, CostEntityType entityType, UUID entityId) {

    // R1: Incomplete calculation → skip variance (understated cost = guaranteed false positive)
    if (!current.isComplete()) {
      log.info("Variance skip: current calculation is incomplete (entityId={})", entityId);
      return;
    }

    CostStage previousStage = previousStageOf(current.getStage());
    if (previousStage == null) return;

    costCalcRepo
        .findActiveByEntityTypeAndEntityIdAndStage(entityType, entityId, previousStage)
        .ifPresent(
            previous -> {
              // R1: Previous stage also must be complete
              if (!previous.isComplete()) {
                log.info("Variance skip: previous stage is incomplete (entityId={})", entityId);
                return;
              }

              // Guard: comparing costs in different currencies is meaningless
              if (!current.getCurrency().equals(previous.getCurrency())) {
                log.warn(
                    "Variance skip: currency mismatch {} vs {} for entityId={}",
                    current.getCurrency(),
                    previous.getCurrency(),
                    entityId);
                return;
              }

              BigDecimal threshold = tenantCostingSettingsPort.getVarianceThreshold(tenantId);
              BigDecimal ratioSigned = current.varianceRatioVs(previous.getTotalCost());
              BigDecimal ratioAbs = ratioSigned.abs();
              if (ratioAbs.compareTo(threshold) > 0) {
                var event =
                    CostVarianceDetectedEvent.builder()
                        .tenantId(tenantId)
                        .costCalculationId(current.getId())
                        .entityType(entityType)
                        .entityId(entityId)
                        .currentStage(current.getStage())
                        .previousStage(previousStage)
                        .previousTotal(previous.getTotalCost())
                        .currentTotal(current.getTotalCost())
                        .varianceRatio(ratioSigned) // signed: + overrun, - saving
                        .currency(current.getCurrency())
                        .build();
                eventPublisher.publishEvent(event);
                log.warn(
                    "CostVarianceDetected: entityType={} entityId={} ratio={} ({} → {})",
                    entityType,
                    entityId,
                    ratioAbs,
                    previous.getTotalCost(),
                    current.getTotalCost());
              }
            });
  }

  private CostStage previousStageOf(CostStage stage) {
    return switch (stage) {
      case ESTIMATED -> null;
      case PLANNED -> CostStage.ESTIMATED;
      case ACTUAL -> CostStage.PLANNED;
    };
  }
}
