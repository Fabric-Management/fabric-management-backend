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
 * description. Nominal values are design values (FABRIC-ARCHITECTURE F8): the widths a fabric is
 * sold in and the widths a lot measured are separate records.
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

    boolean sameAs(Woven other) {
      return other != null
          && same(endsPerCm, other.endsPerCm)
          && same(picksPerCm, other.picksPerCm)
          && Objects.equals(trimToNull(reedNotation), trimToNull(other.reedNotation))
          && Objects.equals(trimToNull(weaveNotation), trimToNull(other.weaveNotation));
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

    boolean sameAs(Knitted other) {
      return other != null
          && Objects.equals(machineGauge, other.machineGauge)
          && same(machineDiameterIn, other.machineDiameterIn)
          && same(coursesPerCm, other.coursesPerCm)
          && same(walesPerCm, other.walesPerCm)
          && same(stitchLengthMm, other.stitchLengthMm)
          && Objects.equals(feeders, other.feeders);
    }
  }

  /** Same values, comparing decimals by value (120 and 120.0 are the same gramme weight). */
  boolean sameAs(FabricArticleSpec other) {
    if (other == null) return false;
    return Objects.equals(trimToNull(articleCode), trimToNull(other.articleCode))
        && Objects.equals(constructionId(construction), constructionId(other.construction))
        && colourState == other.colourState
        && same(gsmFinished, other.gsmFinished)
        && same(gsmGreige, other.gsmGreige)
        && same(nominalFinishedWidthCm, other.nominalFinishedWidthCm)
        && same(usableWidthCm, other.usableWidthCm)
        && same(nominalGreigeWidthCm, other.nominalGreigeWidthCm)
        && same(standardRollLengthM, other.standardRollLengthM)
        && Objects.equals(trimToNull(remarks), trimToNull(other.remarks))
        && (woven == null ? other.woven == null : woven.sameAs(other.woven))
        && (knitted == null ? other.knitted == null : knitted.sameAs(other.knitted))
        && sameLines(byRole(yarns), byRole(other.yarns), YarnLine::sameAs)
        && sameLines(byFibre(composition), byFibre(other.composition), CompositionLine::sameAs);
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
