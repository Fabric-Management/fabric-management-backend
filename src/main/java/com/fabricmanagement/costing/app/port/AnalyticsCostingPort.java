package com.fabricmanagement.costing.app.port;

import com.fabricmanagement.costing.app.port.dto.AnalyticsCostEstimateDto;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface AnalyticsCostingPort {
  /**
   * Retrieves estimated costs for the given set of quote IDs. Only returns calculations where
   * entityType=QUOTE and stage=ESTIMATED.
   *
   * @param tenantId The tenant context.
   * @param quoteIds Set of quote UUIDs to look up.
   * @return A map of quoteId to its estimated cost DTO.
   */
  Map<UUID, AnalyticsCostEstimateDto> getEstimatedCostsByQuoteIds(
      UUID tenantId, Set<UUID> quoteIds);
}
