package com.fabricmanagement.sales.common.domain;

import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import java.math.BigDecimal;

/** A commercial choice; neither a stock reservation nor evidence of customer version approval. */
@io.swagger.v3.oas.annotations.media.Schema(name = "SalesQuantityPolicy", enumAsRef = true)
public enum SalesQuantityPolicy {
  UNRECORDED,
  BY_AGREEMENT,
  EXACT;

  public void assertQuantity(BigDecimal customerRequest, BigDecimal commercial) {
    if (this != EXACT) return;
    if (customerRequest == null
        || commercial == null
        || customerRequest.compareTo(commercial) != 0) {
      throw OrderIntakeException.rule(
          "EXACT_QUANTITY_REQUIRED",
          "The exact customer quantity must be retained; plan a cut if needed");
    }
  }
}
