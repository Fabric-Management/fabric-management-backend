package com.fabricmanagement.production.execution.batch.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;

public interface BatchLotQuantityIntentPort {

  LotIntentCoverage checkCoverage(UUID quoteLineId, Collection<LotIntentRequest> intents);

  LotIntentCoverage replaceIntents(
      UUID quoteId,
      String quoteNumber,
      UUID quoteLineId,
      UUID marketerId,
      String marketerName,
      LocalDate expiresAt,
      Collection<LotIntentRequest> intents);

  void releaseIntents(UUID quoteLineId);

  record LotIntentRequest(UUID batchId, BigDecimal quantity, String unit) {}

  record LotIntentCoverage(boolean covered) {}
}
