package com.fabricmanagement.sales.orderintake.app.adapter;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.stockunit.api.StockUnitLeaseOwnerPort;
import com.fabricmanagement.sales.orderintake.domain.DraftStockSelection;
import com.fabricmanagement.sales.orderintake.infra.repository.DraftStockSelectionRepository;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockUnitLeaseOwnerAdapter implements StockUnitLeaseOwnerPort {
  private final DraftStockSelectionRepository selections;
  private final com.fabricmanagement.production.core.stockunit.api.StockUnitCutSessionOwnerPort
      cuts;

  @Override
  public Map<UUID, UUID> lineOwners(UUID productId, Collection<UUID> sessions) {
    if (sessions.isEmpty()) return Map.of();
    Map<UUID, UUID> owners = new java.util.HashMap<>(cuts.lineOwners(productId, sessions));
    owners.putAll(
        selections
            .findByTenantIdAndProductIdAndSessionIdInAndState(
                TenantContext.requireTenantId(),
                productId,
                sessions,
                DraftStockSelection.State.HELD)
            .stream()
            .filter(row -> sessions.contains(row.getSessionId()) && row.ownLineId() != null)
            .collect(
                Collectors.toMap(
                    DraftStockSelection::getSessionId, DraftStockSelection::ownLineId)));
    return Map.copyOf(owners);
  }
}
