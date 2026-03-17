package com.fabricmanagement.costing.app;

import com.fabricmanagement.costing.domain.calculation.*;
import com.fabricmanagement.costing.domain.event.CostVarianceDetectedEvent;
import com.fabricmanagement.costing.domain.exception.CostingDomainException;
import com.fabricmanagement.costing.domain.exception.PriceListNotFoundException;
import com.fabricmanagement.costing.domain.item.CostItem;
import com.fabricmanagement.costing.domain.price.PriceList;
import com.fabricmanagement.costing.domain.price.PriceListItem;
import com.fabricmanagement.costing.domain.template.CostTemplate;
import com.fabricmanagement.costing.domain.template.CostTemplateItem;
import com.fabricmanagement.costing.infra.repository.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
 * {@link #VARIANCE_THRESHOLD}, a {@link CostVarianceDetectedEvent} is published.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CostCalculationService {

  /** Variance threshold — 10 % deviation triggers the CostVarianceDetectedEvent. */
  private static final BigDecimal VARIANCE_THRESHOLD = new BigDecimal("0.10");

  private final CostItemRepository costItemRepo;
  private final CostTemplateRepository costTemplateRepo;
  private final PriceListRepository priceListRepo;
  private final PriceListItemRepository priceListItemRepo;
  private final CostCalculationRepository costCalcRepo;
  private final ApplicationEventPublisher eventPublisher;

  // ============================================================
  // PUBLIC API
  // ============================================================

  /**
   * Compute or re-compute the ESTIMATED cost (Quote stage).
   *
   * @param tenantId owning tenant
   * @param quoteId the Quote entity ID
   * @param moduleType e.g. "FIBER"
   * @param materialId the primary material being quoted
   * @param totalQuantityKg the projected order quantity
   * @param tradingPartnerId the target customer/supplier (for volume lookups)
   * @return the saved CostCalculation
   */
  @Transactional
  public CostCalculation computeEstimated(
      UUID tenantId,
      UUID quoteId,
      String moduleType,
      UUID materialId,
      BigDecimal totalQuantityKg,
      UUID tradingPartnerId) {
    return compute(
        tenantId,
        CostEntityType.QUOTE,
        quoteId,
        moduleType,
        CostStage.ESTIMATED,
        materialId,
        totalQuantityKg,
        tradingPartnerId);
  }

  /**
   * Compute or re-compute the PLANNED cost (WorkOrder confirmed stage).
   *
   * @param tenantId owning tenant
   * @param workOrderId the WorkOrder entity ID
   * @param moduleType production module
   * @param materialId primary material
   * @param plannedQuantityKg the work order planned quantity
   * @param supplierId the selected supplier (for contracted prices)
   * @return the saved CostCalculation
   */
  @Transactional
  public CostCalculation computePlanned(
      UUID tenantId,
      UUID workOrderId,
      String moduleType,
      UUID materialId,
      BigDecimal plannedQuantityKg,
      UUID supplierId) {
    return compute(
        tenantId,
        CostEntityType.WORK_ORDER,
        workOrderId,
        moduleType,
        CostStage.PLANNED,
        materialId,
        plannedQuantityKg,
        supplierId);
  }

  /**
   * Compute or re-compute the ACTUAL cost (Batch completed stage).
   *
   * @param tenantId owning tenant
   * @param batchId the Batch entity ID
   * @param moduleType production module
   * @param materialId the batch material
   * @param actualQuantityKg actual quantity produced (net of waste)
   * @param supplierId the supplier used
   * @return the saved CostCalculation
   */
  @Transactional
  public CostCalculation computeActual(
      UUID tenantId,
      UUID batchId,
      String moduleType,
      UUID materialId,
      BigDecimal actualQuantityKg,
      UUID supplierId) {
    return compute(
        tenantId,
        CostEntityType.BATCH,
        batchId,
        moduleType,
        CostStage.ACTUAL,
        materialId,
        actualQuantityKg,
        supplierId);
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
      UUID materialId,
      BigDecimal quantityKg,
      UUID tradingPartnerId) {

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
            .findActiveForModule(tenantId, moduleType, LocalDate.now())
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

    // 4. Build calculation
    var calc =
        CostCalculation.create(
            tenantId, entityType, entityId, moduleType, stage, priceList.getCurrency());
    calc.setCostTemplateId(template.getId());

    List<CostItem> relevantItems = costItemRepo.findActiveForModule(moduleType);

    for (CostTemplateItem templateItem : template.getItems()) {
      if (!templateItem.isIncluded()) continue;

      Optional<CostItem> costItemOpt =
          relevantItems.stream()
              .filter(ci -> ci.getCode().equals(templateItem.costItemCode()))
              .findFirst();
      if (costItemOpt.isEmpty()) {
        log.warn("CostItem '{}' in template not found — skipping", templateItem.costItemCode());
        continue;
      }

      CostItem costItem = costItemOpt.get();

      Optional<PriceListItem> priceItemOpt =
          priceListItemRepo.findBest(
              priceList.getId(), costItem.getCode(), materialId, tradingPartnerId);

      if (priceItemOpt.isEmpty()) {
        log.debug(
            "No price found for cost item '{}' in price list {} — skipping",
            costItem.getCode(),
            priceList.getId());
        continue;
      }

      PriceListItem priceItem = priceItemOpt.get();
      BigDecimal effectiveUnitPrice = priceItem.resolveUnitPrice(quantityKg);
      boolean volumeDiscountApplied = !effectiveUnitPrice.equals(priceItem.getUnitPrice());

      // Calculate line total
      BigDecimal lineTotal =
          computeLineTotal(
              costItem, effectiveUnitPrice, quantityKg, templateItem.weight(), calc.getTotalCost());

      // Unit is derived from CalculationBase — not hardcoded
      String unit =
          switch (costItem.getCalculationBase()) {
            case PER_KG -> "KG";
            case PER_HOUR -> "HOUR";
            case PER_UNIT -> "UNIT";
            // PERCENTAGE and FIXED lines have no meaningful quantity unit
            case PERCENTAGE, FIXED -> null;
          };

      // Lombok @Builder does not include inherited BaseEntity fields — use setter approach
      // NOTE: costCalculationId is intentionally NOT set here.
      // JPA resolves it automatically via @JoinColumn(name="cost_calculation_id") on
      // CostCalculation.lines when the parent is cascade-persisted. Setting it to
      // calc.getId() before the parent is flushed would write null to the FK column.
      CostCalculationLine line = new CostCalculationLine();
      line.setTenantId(tenantId);
      line.setCostItemCode(costItem.getCode());
      line.setQty(quantityKg);
      line.setUnit(unit);
      line.setUnitPrice(effectiveUnitPrice);
      line.setCurrency(priceItem.getCurrency());
      line.setTotalInBaseCurrency(lineTotal);
      line.setVolumeDiscountApplied(volumeDiscountApplied);

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

  /** Compute the total amount for a single cost line based on the item's calculation base. */
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
    CostStage previousStage = previousStageOf(current.getStage());
    if (previousStage == null) return;

    costCalcRepo
        .findByEntityTypeAndEntityIdAndStage(entityType, entityId, previousStage)
        .ifPresent(
            previous -> {
              // Fix #3: compute once, reuse for threshold check and event payload
              BigDecimal ratioSigned = current.varianceRatioVs(previous.getTotalCost());
              BigDecimal ratioAbs = ratioSigned.abs();
              if (ratioAbs.compareTo(VARIANCE_THRESHOLD) > 0) {
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
                        .detectedAt(Instant.now())
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
