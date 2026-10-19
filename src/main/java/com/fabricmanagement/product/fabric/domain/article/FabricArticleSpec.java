package com.fabricmanagement.product.fabric.domain.article;

import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.domain.reference.FabricConstruction;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricColourState;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricYarnRole;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything about a fabric article that a tenant edits, apart from the product's own name and
 * description. Two kinds of value live here (FABRIC-ARCHITECTURE §1d):
 *
 * <ul>
 *   <li><b>Identity</b> — what makes it this fabric: construction, greige gramme weight, woven
 *       ends/picks, reed and weave, knitted gauge, stitch length and course/wale density, yarns and
 *       composition. Changing one raises the article's spec version.
 *   <li><b>Recommended production settings</b> — colour state, finished gramme weight, the nominal
 *       widths, standard roll length, knitting machine diameter and feeders, remarks. A weaving or
 *       knitting order takes them as defaults and may change them; the values actually used belong
 *       to the order and the lot. Changing them never raises the spec version.
 * </ul>
 */
public record FabricArticleSpec(
    String articleCode,
    FabricConstruction construction,
    FabricColourState colourState,
    BigDecimal gsmFinished,
    BigDecimal gsmGreige,
    BigDecimal nominalFinishedWidthCm,
    BigDecimal usableWidthCm,
    BigDecimal nominalGreigeWidthCm,
    BigDecimal standardRollLengthM,
    String remarks,
    Woven woven,
    Knitted knitted,
    List<YarnLine> yarns,
    List<CompositionLine> composition) {

  public FabricArticleSpec {
    yarns = yarns == null ? List.of() : List.copyOf(yarns);
    composition = composition == null ? List.of() : List.copyOf(composition);
  }

  /** One yarn: its role, the yarn product, its share of the fabric's mass and its designation. */
  public record YarnLine(
      FabricYarnRole role, UUID yarnProductId, BigDecimal sharePercent, String designation) {

    boolean sameAs(YarnLine other) {
      return role == other.role
          && Objects.equals(yarnProductId, other.yarnProductId)
          && same(sharePercent, other.sharePercent);
    }
  }

  /** One fibre of the declared composition, with the fibre's ISO code and name as snapshots. */
  public record CompositionLine(
      UUID fiberId,
      String isoCode,
      String fiberName,
      MaterialSource materialSource,
      BigDecimal percentage) {

    boolean sameAs(CompositionLine other) {
      return Objects.equals(fiberId, other.fiberId)
          && materialSource == other.materialSource
          && same(percentage, other.percentage);
    }
  }

  /** Woven design values: thread density, reed and weave notation. */
  public record Woven(
      BigDecimal endsPerCm, BigDecimal picksPerCm, String reedNotation, String weaveNotation) {

    public static final Woven EMPTY = new Woven(null, null, null, null);

    boolean revises(Woven previous) {
      return revised(previous.endsPerCm, endsPerCm)
          || revised(previous.picksPerCm, picksPerCm)
          || revised(previous.reedNotation, reedNotation)
          || revised(previous.weaveNotation, weaveNotation);
    }
  }

  /** Knitted design values: machine and stitch. */
  public record Knitted(
      Integer machineGauge,
      BigDecimal machineDiameterIn,
      BigDecimal coursesPerCm,
      BigDecimal walesPerCm,
      BigDecimal stitchLengthMm,
      Integer feeders) {

    public static final Knitted EMPTY = new Knitted(null, null, null, null, null, null);

    /**
     * Diameter and feeders are machine settings, not identity: the same fabric runs on 30" or 34".
     */
    boolean revises(Knitted previous) {
      return revised(previous.machineGauge, machineGauge)
          || revised(previous.coursesPerCm, coursesPerCm)
          || revised(previous.walesPerCm, walesPerCm)
          || revised(previous.stitchLengthMm, stitchLengthMm);
    }
  }

  /**
   * Whether this spec, replacing {@code previous}, changes the fabric's identity. Filling in an
   * identity value that was still empty completes the fabric rather than changing it, so it does
   * not count; replacing or removing one does. Decimals compare by value (120 and 120.0 are one
   * gramme weight).
   */
  boolean revisesIdentityOf(FabricArticleSpec previous) {
    return (previous.construction != null
            && !Objects.equals(constructionId(previous.construction), constructionId(construction)))
        || revised(previous.gsmGreige, gsmGreige)
        || (woven != null && previous.woven != null && woven.revises(previous.woven))
        || (knitted != null && previous.knitted != null && knitted.revises(previous.knitted))
        || (!previous.yarns.isEmpty()
            && !sameLines(byRole(yarns), byRole(previous.yarns), YarnLine::sameAs))
        || (!previous.composition.isEmpty()
            && !sameLines(
                byFibre(composition), byFibre(previous.composition), CompositionLine::sameAs));
  }

  private static boolean revised(BigDecimal previous, BigDecimal next) {
    return previous != null && !same(previous, next);
  }

  private static boolean revised(Integer previous, Integer next) {
    return previous != null && !previous.equals(next);
  }

  private static boolean revised(String previous, String next) {
    return trimToNull(previous) != null && !Objects.equals(trimToNull(previous), trimToNull(next));
  }

  private static <T> boolean sameLines(
      List<T> left, List<T> right, java.util.function.BiPredicate<T, T> same) {
    if (left.size() != right.size()) return false;
    for (int index = 0; index < left.size(); index++) {
      if (!same.test(left.get(index), right.get(index))) return false;
    }
    return true;
  }

  /** Yarns in role order; the order within a role is the yarns' positions, so it is kept. */
  private static List<YarnLine> byRole(List<YarnLine> lines) {
    return lines.stream()
        .sorted(Comparator.comparing((YarnLine line) -> line.role().ordinal()))
        .toList();
  }

  /** Composition order carries no meaning, so lines compare by fibre. */
  private static List<CompositionLine> byFibre(List<CompositionLine> lines) {
    return lines.stream()
        .sorted(
            Comparator.comparing(CompositionLine::fiberId)
                .thenComparing(
                    line -> line.materialSource() == null ? "" : line.materialSource().name()))
        .toList();
  }

  /** A share of the fabric: above zero, at most 100, to two decimal places. */
  static BigDecimal percentage(String field, BigDecimal value) {
    if (value == null || value.signum() <= 0 || value.compareTo(new BigDecimal("100")) > 0) {
      throw new FabricDomainException(field + " must be above 0 and at most 100");
    }
    if (value.stripTrailingZeros().scale() > 2) {
      throw new FabricDomainException(field + " takes at most two decimal places");
    }
    return value;
  }

  /** The sum of the given percentages, exactly. */
  static BigDecimal total(List<BigDecimal> values) {
    return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static Object constructionId(FabricConstruction construction) {
    return construction == null ? null : construction.getId();
  }

  static boolean same(BigDecimal left, BigDecimal right) {
    return left == null ? right == null : right != null && left.compareTo(right) == 0;
  }

  static BigDecimal positiveOrNull(String field, BigDecimal value) {
    if (value != null && value.signum() <= 0) {
      throw new FabricDomainException(field + " must be greater than zero");
    }
    return value;
  }

  static Integer positiveOrNull(String field, Integer value) {
    if (value != null && value <= 0) {
      throw new FabricDomainException(field + " must be greater than zero");
    }
    return value;
  }

  static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
