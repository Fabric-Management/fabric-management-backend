package com.fabricmanagement.product.fabric.domain.article;

import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.domain.reference.FabricConstruction;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricColourState;
import java.math.BigDecimal;
import java.util.Objects;

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
    Knitted knitted) {

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
        && (knitted == null ? other.knitted == null : knitted.sameAs(other.knitted));
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
