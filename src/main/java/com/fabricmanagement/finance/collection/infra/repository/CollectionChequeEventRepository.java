package com.fabricmanagement.finance.collection.infra.repository;

import com.fabricmanagement.finance.collection.domain.CollectionChequeEvent;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CollectionChequeEventRepository
    extends JpaRepository<CollectionChequeEvent, UUID> {
  @Query(
      "select max(e.effectiveDate) from CollectionChequeEvent e where e.tenantId=:tenant and"
          + " e.chequeId=:cheque")
  java.time.LocalDate latestEffectiveDate(UUID tenant, UUID cheque);

  org.springframework.data.domain.Page<CollectionChequeEvent> findByTenantIdAndChequeId(
      UUID tenant, UUID cheque, org.springframework.data.domain.Pageable page);

  List<CollectionChequeEvent> findByTenantIdAndChequeIdInAndAction(
      UUID tenant, List<UUID> chequeIds, String action);

  @Query(
      """
      select distinct e.chequeId from CollectionChequeEvent e
      where e.tenantId = :tenant and e.chequeId in :ids and e.action in ('CORRECT', 'CORRECT_DRAWER')
      """)
  Set<UUID> correctedCheques(UUID tenant, List<UUID> ids);
}
