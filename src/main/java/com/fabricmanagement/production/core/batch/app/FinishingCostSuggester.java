package com.fabricmanagement.production.core.batch.app;

import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingBillingBasis;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
import com.fabricmanagement.product.fabric.dto.FabricFinishingPriceDto;
import com.fabricmanagement.product.qualitygrade.api.query.QualityGradeQueryService.QualityGradeReference;
import com.fabricmanagement.production.core.batch.domain.FinishingCostLineStatus;
import com.fabricmanagement.production.core.batch.domain.FinishingGradeBreakdown;
import com.fabricmanagement.production.core.batch.domain.FinishingGradeMetres;
import com.fabricmanagement.production.core.batch.dto.BatchFinishingDtos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * What a firm should charge for a delivery by the price list (FABRIC-3c-2b): for each returned
 * grade the price that applies to it, times the metres that price counts. It works from the prices
 * already picked for the lot (see {@code FinishingPriceResolver}) and holds no state.
 *
 * <ul>
 *   <li>A grade uses its own price, else the price without a grade. A grade with neither is not
 *       billed, because the firm prices other grades and leaves this one out.
 *   <li>A price on returned metres multiplies the grade's returned metres. A price on sent metres
 *       multiplies the sent metres, shared out among the grades by their part of the return.
 *   <li>A price per kilogram cannot be worked out: the lot has no weight.
 *   <li>With no split, the whole delivery is one line priced by the price without a grade; if the
 *       firm only prices individual grades, the split is needed first.
 *   <li>The total is per currency, since prices can differ in it.
 * </ul>
 */
final class FinishingCostSuggester {

  private static final int METRE_SCALE = 3;
  private static final int MONEY_SCALE = 2;

  private FinishingCostSuggester() {}

  static BatchFinishingDtos.CostSuggestionDto suggest(
      LocalDate priceDate,
      BigDecimal sentMetres,
      BigDecimal returnedMetres,
      FinishingGradeBreakdown breakdown,
      List<FabricFinishingPriceDto> prices,
      Map<UUID, QualityGradeReference> grades) {
    FabricFinishingPriceDto ungraded =
        prices.stream().filter(price -> price.qualityGradeId() == null).findFirst().orElse(null);
    Map<UUID, FabricFinishingPriceDto> byGrade = new java.util.HashMap<>();
    prices.stream()
        .filter(price -> price.qualityGradeId() != null)
        .forEach(price -> byGrade.putIfAbsent(price.qualityGradeId(), price));

    List<BatchFinishingDtos.CostSuggestionLineDto> lines = new ArrayList<>();
    if (breakdown.isEmpty()) {
      lines.add(
          line(null, null, returnedMetres, ungraded, prices.isEmpty(), sentMetres, returnedMetres));
    } else {
      List<FinishingGradeMetres> ordered =
          breakdown.rows().stream()
              .sorted(
                  Comparator.comparingInt(
                          (FinishingGradeMetres row) -> rank(grades, row.qualityGradeId()))
                      .thenComparing(FinishingGradeMetres::qualityGradeId))
              .toList();
      for (FinishingGradeMetres row : ordered) {
        FabricFinishingPriceDto price = byGrade.getOrDefault(row.qualityGradeId(), ungraded);
        lines.add(
            line(
                row.qualityGradeId(),
                grades.get(row.qualityGradeId()),
                row.metres(),
                price,
                prices.isEmpty(),
                sentMetres,
                returnedMetres));
      }
    }

    Map<String, BigDecimal> totals = new TreeMap<>();
    boolean complete = true;
    for (BatchFinishingDtos.CostSuggestionLineDto line : lines) {
      if (line.status() == FinishingCostLineStatus.PRICED) {
        totals.merge(line.currency(), line.amount(), BigDecimal::add);
      } else if (line.status() != FinishingCostLineStatus.NOT_BILLED) {
        complete = false;
      }
    }
    return new BatchFinishingDtos.CostSuggestionDto(
        priceDate,
        complete,
        List.copyOf(lines),
        totals.entrySet().stream()
            .map(
                entry ->
                    new BatchFinishingDtos.CostSuggestionTotalDto(entry.getKey(), entry.getValue()))
            .toList());
  }

  /**
   * One line. {@code noPrice} says nothing can be priced for the line: the firm has no price for
   * the work at all, or the delivery is unsplit and only individual grades are priced.
   */
  private static BatchFinishingDtos.CostSuggestionLineDto line(
      UUID gradeId,
      QualityGradeReference grade,
      BigDecimal metres,
      FabricFinishingPriceDto price,
      boolean noPrice,
      BigDecimal sentMetres,
      BigDecimal returnedMetres) {
    String code = grade == null ? null : grade.code();
    String name = grade == null ? null : grade.name();
    if (price == null) {
      FinishingCostLineStatus status =
          noPrice || gradeId == null
              ? FinishingCostLineStatus.NO_PRICE
              : FinishingCostLineStatus.NOT_BILLED;
      return new BatchFinishingDtos.CostSuggestionLineDto(
          gradeId, code, name, metres, status, null, null, null, null, null, null, null);
    }
    if (price.unit() == FinishingChargeUnit.KG) {
      return priced(
          gradeId, code, name, metres, price, FinishingCostLineStatus.NOT_COMPUTABLE, null, null);
    }
    BigDecimal billed =
        price.billingBasis() == FinishingBillingBasis.SENT
            ? sentMetres.multiply(metres).divide(returnedMetres, METRE_SCALE, RoundingMode.HALF_UP)
            : metres;
    BigDecimal amount =
        billed.multiply(price.unitPrice()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    return priced(
        gradeId, code, name, metres, price, FinishingCostLineStatus.PRICED, billed, amount);
  }

  private static BatchFinishingDtos.CostSuggestionLineDto priced(
      UUID gradeId,
      String code,
      String name,
      BigDecimal metres,
      FabricFinishingPriceDto price,
      FinishingCostLineStatus status,
      BigDecimal billed,
      BigDecimal amount) {
    return new BatchFinishingDtos.CostSuggestionLineDto(
        gradeId,
        code,
        name,
        metres,
        status,
        price.id(),
        price.billingBasis(),
        price.unit(),
        price.unitPrice(),
        price.currency(),
        billed,
        amount);
  }

  private static int rank(Map<UUID, QualityGradeReference> grades, UUID gradeId) {
    QualityGradeReference grade = grades.get(gradeId);
    return grade == null ? Integer.MAX_VALUE : grade.rank();
  }
}
