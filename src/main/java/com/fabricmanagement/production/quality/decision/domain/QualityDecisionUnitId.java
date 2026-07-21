package com.fabricmanagement.production.quality.decision.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class QualityDecisionUnitId implements Serializable {
  @Serial private static final long serialVersionUID = 1L;

  private UUID tenantId;
  private UUID decisionId;
  private UUID stockUnitId;

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof QualityDecisionUnitId that)) {
      return false;
    }
    return Objects.equals(tenantId, that.tenantId)
        && Objects.equals(decisionId, that.decisionId)
        && Objects.equals(stockUnitId, that.stockUnitId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tenantId, decisionId, stockUnitId);
  }
}
