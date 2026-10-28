package com.fabricmanagement.production.core.stockunit.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.stockunit.api.StockUnitCutSessionOwnerPort;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitCutSessionRepository;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockUnitCutSessionOwners implements StockUnitCutSessionOwnerPort {
  private final StockUnitCutSessionRepository sessions;

  @Override
  public Map<UUID, UUID> lineOwners(UUID product, Collection<UUID> ids) {
    if (ids.isEmpty()) return Map.of();
    return sessions
        .findByTenantIdAndProductIdAndSessionIdInAndEndedFalse(
            TenantContext.requireTenantId(), product, ids)
        .stream()
        .collect(
            Collectors.toMap(
                com.fabricmanagement.production.core.stockunit.domain.StockUnitCutSession
                    ::getSessionId,
                com.fabricmanagement.production.core.stockunit.domain.StockUnitCutSession
                    ::getTargetLineId));
  }
}
