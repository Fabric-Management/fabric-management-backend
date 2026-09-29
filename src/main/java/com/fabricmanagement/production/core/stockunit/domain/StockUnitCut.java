package com.fabricmanagement.production.core.stockunit.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A cut taken from a piece (SOI A06). The remaining length is stated at cut time but becomes a
 * usable fact only once someone verifies it; until then the piece never enters a whole-piece
 * proposal.
 */
@Entity
@Table(name = "stock_unit_cut", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockUnitCut extends BaseEntity {

  @Column(name = "stock_unit_id", nullable = false, updatable = false)
  private UUID stockUnitId;

  @Column(name = "cut_length", nullable = false, updatable = false, precision = 15, scale = 3)
  private BigDecimal cutLength;

  @Column(name = "remaining_length", nullable = false, updatable = false, precision = 15, scale = 3)
  private BigDecimal remainingLength;

  @Column(name = "length_unit", nullable = false, updatable = false, length = 10)
  private String lengthUnit;

  @Column(name = "recorded_by", nullable = false, updatable = false)
  private UUID recordedBy;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  @Column(name = "remaining_verified_by")
  private UUID remainingVerifiedBy;

  @Column(name = "remaining_verified_at")
  private Instant remainingVerifiedAt;

  public static StockUnitCut record(
      UUID stockUnitId,
      BigDecimal cutLength,
      BigDecimal remainingLength,
      String lengthUnit,
      UUID recordedBy,
      Instant recordedAt) {
    if (stockUnitId == null || recordedBy == null || recordedAt == null) {
      throw new IllegalArgumentException("Piece, recorder and time are required");
    }
    if (cutLength == null || cutLength.signum() <= 0) {
      throw new IllegalArgumentException("Cut length must be positive");
    }
    if (remainingLength == null || remainingLength.signum() < 0) {
      throw new IllegalArgumentException("Remaining length cannot be negative");
    }
    if (lengthUnit == null || lengthUnit.isBlank()) {
      throw new IllegalArgumentException("Length unit is required");
    }
    StockUnitCut cut = new StockUnitCut();
    cut.stockUnitId = stockUnitId;
    cut.cutLength = cutLength;
    cut.remainingLength = remainingLength;
    cut.lengthUnit = lengthUnit.trim();
    cut.recordedBy = recordedBy;
    cut.recordedAt = recordedAt;
    return cut;
  }

  public void verifyRemaining(UUID verifier, Instant at) {
    if (remainingVerifiedAt != null) {
      throw new IllegalStateException("The remaining length is already verified");
    }
    if (verifier == null || at == null) {
      throw new IllegalArgumentException("Verifier and time are required");
    }
    this.remainingVerifiedBy = verifier;
    this.remainingVerifiedAt = at;
  }

  public boolean isRemainingVerified() {
    return remainingVerifiedAt != null;
  }

  @Override
  protected String getModuleCode() {
    return "SUC";
  }
}
