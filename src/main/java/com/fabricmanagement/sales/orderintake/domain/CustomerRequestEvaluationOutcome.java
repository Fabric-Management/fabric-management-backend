package com.fabricmanagement.sales.orderintake.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Result of the technical evaluation of a custom request (SOI R17). */
@Schema(name = "CustomerRequestEvaluationOutcome", enumAsRef = true)
public enum CustomerRequestEvaluationOutcome {
  MATCH_EXISTING,
  NEW_PRODUCT,
  EQUIVALENT,
  NEEDS_INFO,
  NOT_FEASIBLE;

  public boolean hasSolution() {
    return this == MATCH_EXISTING || this == NEW_PRODUCT || this == EQUIVALENT;
  }
}
