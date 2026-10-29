package com.fabricmanagement.production.core.batch.domain;

import com.fabricmanagement.production.core.batch.domain.exception.BatchDomainException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The metres a finishing step returned, split by quality grade (FABRIC-3c-2b). A dyehouse sorts
 * what it sends back into 1A, 2A, B and so on and prices the grades separately, so the split is
 * what the amount is worked out from. A split is optional; when given, every grade appears once
 * with positive metres and the rows add up to exactly the returned metres, because a split that
 * does not account for the whole delivery would price part of the lot silently.
 */
public final class FinishingGradeBreakdown {

  private static final int METRE_SCALE = 3;
  private static final FinishingGradeBreakdown NONE = new FinishingGradeBreakdown(List.of());

  private final List<FinishingGradeMetres> rows;

  private FinishingGradeBreakdown(List<FinishingGradeMetres> rows) {
    this.rows = rows;
  }

  /** No split: the firm did not sort the delivery by grade. */
  public static FinishingGradeBreakdown none() {
    return NONE;
  }

  public static FinishingGradeBreakdown of(Collection<FinishingGradeMetres> rows) {
    if (rows == null || rows.isEmpty()) {
      return NONE;
    }
    Set<UUID> seen = new HashSet<>();
    List<FinishingGradeMetres> checked = new ArrayList<>();
    for (FinishingGradeMetres row : rows) {
      if (row == null || row.qualityGradeId() == null) {
        throw new BatchDomainException(
            "Every grade row needs a quality grade", "FINISHING_GRADE_METRES_INVALID", 422);
      }
      if (!seen.add(row.qualityGradeId())) {
        throw new BatchDomainException(
            "A quality grade can appear only once in the split", "FINISHING_GRADE_DUPLICATE", 422);
      }
      checked.add(new FinishingGradeMetres(row.qualityGradeId(), requireMetres(row.metres())));
    }
    return new FinishingGradeBreakdown(List.copyOf(checked));
  }

  public List<FinishingGradeMetres> rows() {
    return rows;
  }

  public boolean isEmpty() {
    return rows.isEmpty();
  }

  public BigDecimal total() {
    return rows.stream().map(FinishingGradeMetres::metres).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** A split must account for the whole delivery; no split has nothing to add up. */
  public void requireTotal(BigDecimal returnedMetres) {
    if (rows.isEmpty()) {
      return;
    }
    if (returnedMetres == null || total().compareTo(returnedMetres) != 0) {
      throw new BatchDomainException(
          "The grade metres add up to "
              + total().toPlainString()
              + " but the lot returned "
              + (returnedMetres == null ? "nothing" : returnedMetres.toPlainString()),
          "FINISHING_GRADE_METRES_MISMATCH",
          422);
    }
  }

  private static BigDecimal requireMetres(BigDecimal value) {
    if (value == null || value.signum() <= 0) {
      throw new BatchDomainException(
          "Grade metres must be positive", "FINISHING_GRADE_METRES_INVALID", 422);
    }
    if (value.stripTrailingZeros().scale() > METRE_SCALE) {
      throw new BatchDomainException(
          "Grade metres allow at most three decimals", "FINISHING_GRADE_METRES_INVALID", 422);
    }
    return value.setScale(METRE_SCALE, RoundingMode.UNNECESSARY);
  }
}
