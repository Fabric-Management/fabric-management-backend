package com.fabricmanagement.production.execution.batch.domain.exception;

import com.fabricmanagement.production.common.exception.ProductionDomainException;
import java.math.BigDecimal;
import java.util.UUID;

public class LotIntentQuantityExceededException extends ProductionDomainException {

  public LotIntentQuantityExceededException(
      UUID batchId, BigDecimal requestedQuantity, BigDecimal physicalQuantity, String unit) {
    super(
        "Lot quantity intent exceeds physical quantity for batch "
            + batchId
            + ": requested "
            + requestedQuantity
            + " "
            + unit
            + ", physical "
            + physicalQuantity
            + " "
            + unit,
        "PRODUCTION_015_LOT_INTENT_QUANTITY_EXCEEDED",
        422);
  }
}
