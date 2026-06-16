package com.fabricmanagement.costing.app.port.impl;

import com.fabricmanagement.costing.app.port.AnalyticsCostingPort;
import com.fabricmanagement.costing.app.port.dto.AnalyticsCostEstimateDto;
import com.fabricmanagement.costing.domain.calculation.CostCalculation;
import com.fabricmanagement.costing.domain.calculation.CostEntityType;
import com.fabricmanagement.costing.domain.calculation.CostStage;
import com.fabricmanagement.costing.infra.repository.CostCalculationRepository;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalyticsCostingPortImpl implements AnalyticsCostingPort {

  private final CostCalculationRepository costCalculationRepository;

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, AnalyticsCostEstimateDto> getEstimatedCostsByQuoteIds(
      UUID tenantId, Set<UUID> quoteIds) {

    if (quoteIds == null || quoteIds.isEmpty()) {
      return Map.of();
    }

    return costCalculationRepository
        .findActiveByTenantIdAndEntityTypeAndStageAndEntityIdIn(
            tenantId, CostEntityType.QUOTE, CostStage.ESTIMATED, quoteIds)
        .stream()
        .collect(
            Collectors.toMap(
                CostCalculation::getEntityId,
                cc ->
                    AnalyticsCostEstimateDto.builder()
                        .totalCost(
                            com.fabricmanagement.common.util.Money.of(
                                cc.getTotalCost(), cc.getCurrency()))
                        .complete(cc.isComplete())
                        .build()));
  }
}
