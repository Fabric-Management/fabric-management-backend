package com.fabricmanagement.sales.orderintake.infra.repository;

import com.fabricmanagement.sales.orderintake.domain.DraftStockSelection;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DraftStockSelectionRepository extends JpaRepository<DraftStockSelection, UUID> {
  Optional<DraftStockSelection> findByTenantIdAndId(UUID tenantId, UUID id);

  List<DraftStockSelection> findByTenantIdAndProductIdAndSessionIdInAndState(
      UUID tenantId, UUID productId, Collection<UUID> sessionIds, DraftStockSelection.State state);

  @org.springframework.data.jpa.repository.Query(
      """
      select s from DraftStockSelection s where s.tenantId=:tenant and s.isActive=true
        and (s.targetLineId=:line or s.targetQuoteLineId=:line) and s.state in :states
      order by s.productId, s.id
      """)
  List<DraftStockSelection> findMutableTargetRows(
      @org.springframework.data.repository.query.Param("tenant") UUID tenant,
      @org.springframework.data.repository.query.Param("line") UUID line,
      @org.springframework.data.repository.query.Param("states")
          Collection<DraftStockSelection.State> states);

  default List<DraftStockSelection> findMutableTargets(UUID tenant, UUID line) {
    return findMutableTargetRows(
        tenant,
        line,
        List.of(
            DraftStockSelection.State.OPEN,
            DraftStockSelection.State.HELD,
            DraftStockSelection.State.RELEASED));
  }

  List<DraftStockSelection> findByTenantIdAndTargetQuoteIdAndStateIn(
      UUID tenant, UUID quoteId, Collection<DraftStockSelection.State> states);

  List<DraftStockSelection> findByTenantIdAndTargetOrderIdAndState(
      UUID tenantId, UUID orderId, DraftStockSelection.State state);

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
