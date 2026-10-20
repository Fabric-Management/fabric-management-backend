package com.fabricmanagement.sales.orderintake.infra.repository;

import com.fabricmanagement.sales.orderintake.domain.DraftStockSelection;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DraftStockSelectionRepository extends JpaRepository<DraftStockSelection, UUID> {
  Optional<DraftStockSelection> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<DraftStockSelection> findByTenantIdAndFormInstanceIdAndClientLineIdAndActorIdAndStateIn(
      UUID tenantId,
      UUID form,
      UUID line,
      UUID actor,
      Collection<DraftStockSelection.State> states);

  List<DraftStockSelection> findByTenantIdAndSalesOrderLineIdInAndState(
      UUID tenantId, Collection<UUID> lines, DraftStockSelection.State state);

  List<DraftStockSelection> findByTenantIdAndSalesOrderIdAndState(
      UUID tenantId, UUID orderId, DraftStockSelection.State state);

  List<DraftStockSelection> findByTenantIdAndProductIdAndState(
      UUID tenantId, UUID productId, DraftStockSelection.State state);
}
