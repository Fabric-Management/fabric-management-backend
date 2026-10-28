package com.fabricmanagement.finance.collection.app.adapter;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.app.port.CollectionSettlementPort;
import com.fabricmanagement.finance.collection.domain.CollectionObligation;
import com.fabricmanagement.finance.collection.infra.repository.CollectionObligationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CollectionSettlementAdapter implements CollectionSettlementPort {
  private final CollectionObligationRepository obligations;
  private final CollectionPaymentMatchRepository matches;

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, BigDecimal> settledByObligation(UUID tenantId, UUID orderId) {
    // A caller that asks about another tenant is a bug; answering "nothing collected" would turn
    // a safety check into a pass.
    if (!TenantContext.requireTenantId().equals(tenantId))
      throw new IllegalStateException("Settlement was requested outside the bound tenant");
    var rows = obligations.findByTenantIdAndOrderId(tenantId, orderId);
    if (rows.isEmpty()) return Map.of();
    Map<UUID, UUID> logicalOf = new HashMap<>();
    for (CollectionObligation row : rows) logicalOf.put(row.getId(), row.getLogicalId());
    Map<UUID, BigDecimal> settled = new HashMap<>();
    for (var balance : matches.settled(tenantId, List.copyOf(logicalOf.keySet()))) {
      UUID logical = logicalOf.get(balance.getId());
      if (logical != null && balance.getAmount().signum() > 0)
        settled.merge(logical, balance.getAmount(), BigDecimal::add);
    }
    return Map.copyOf(settled);
  }
}
