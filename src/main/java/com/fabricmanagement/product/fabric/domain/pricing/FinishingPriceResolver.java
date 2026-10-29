package com.fabricmanagement.product.fabric.domain.pricing;

import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Picks the finishing prices in force (FABRIC-3c). A firm prices the work in its own way, so the
 * result is a price per quality grade, not one price:
 *
 * <ol>
 *   <li>Only prices in the process's current charge unit that have started by the date and fit the
 *       lot ({@link FabricFinishingPrice#matches}) are candidates, so a process moved from metres
 *       to kilograms never reads an old per-metre price as per-kilogram.
 *   <li>At a firm, if the firm has any candidate of its own, only the firm's candidates are used
 *       and the general prices are not consulted: a firm's tariff is the whole tariff, not a patch
 *       on the general one. Otherwise (or at an own site) the general candidates are used.
 *   <li>The candidates are grouped by quality grade, and each group yields its most specific price,
 *       the latest started among equally specific ones.
 * </ol>
 *
 * <p>A winner without a grade applies to every grade that has no winner of its own; a grade with
 * neither is not billed. The winners come back with the ungraded one first.
 */
public final class FinishingPriceResolver {

  private static final Comparator<FabricFinishingPrice> STRONGER =
      Comparator.comparingInt(FabricFinishingPrice::specificity)
          .thenComparing(FabricFinishingPrice::getValidFrom)
          .thenComparing(
              FabricFinishingPrice::getId, Comparator.nullsFirst(Comparator.naturalOrder()));

  private static final Comparator<FabricFinishingPrice> BY_GRADE =
      Comparator.comparing(
          FabricFinishingPrice::getQualityGradeId,
          Comparator.nullsFirst(Comparator.<UUID>naturalOrder()));

  private FinishingPriceResolver() {}

  public static List<FabricFinishingPrice> resolve(
      Collection<FabricFinishingPrice> prices,
      FinishingChargeUnit unit,
      UUID partnerId,
      LocalDate date,
      PriceContext context) {
    List<FabricFinishingPrice> candidates =
        prices.stream()
            .filter(price -> price.getUnit() == unit && price.startedBy(date))
            .filter(price -> price.matches(context))
            .toList();
    List<FabricFinishingPrice> firm =
        partnerId == null
            ? List.of()
            : candidates.stream().filter(price -> partnerId.equals(price.getPartnerId())).toList();
    List<FabricFinishingPrice> pool =
        firm.isEmpty() ? candidates.stream().filter(price -> !price.isFirmTariff()).toList() : firm;

    Map<UUID, FabricFinishingPrice> winners = new HashMap<>();
    FabricFinishingPrice ungraded = null;
    for (FabricFinishingPrice price : pool) {
      if (price.getQualityGradeId() == null) {
        ungraded = stronger(ungraded, price);
      } else {
        winners.merge(price.getQualityGradeId(), price, FinishingPriceResolver::stronger);
      }
    }
    List<FabricFinishingPrice> result = new ArrayList<>();
    if (ungraded != null) {
      result.add(ungraded);
    }
    winners.values().stream().sorted(BY_GRADE).forEach(result::add);
    return List.copyOf(result);
  }

  private static FabricFinishingPrice stronger(FabricFinishingPrice a, FabricFinishingPrice b) {
    if (a == null) {
      return b;
    }
    return STRONGER.compare(a, b) >= 0 ? a : b;
  }
}
