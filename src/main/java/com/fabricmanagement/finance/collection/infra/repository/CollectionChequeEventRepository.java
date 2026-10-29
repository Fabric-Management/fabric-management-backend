package com.fabricmanagement.finance.collection.infra.repository;

import com.fabricmanagement.finance.collection.domain.CollectionChequeEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionChequeEventRepository
    extends JpaRepository<CollectionChequeEvent, UUID> {
  @org.springframework.data.jpa.repository.Query(
      "select max(e.effectiveDate) from CollectionChequeEvent e where e.tenantId=:tenant and"
          + " e.chequeId=:cheque")
  java.time.LocalDate latestEffectiveDate(UUID tenant, UUID cheque);

  org.springframework.data.domain.Page<CollectionChequeEvent> findByTenantIdAndChequeId(
      UUID tenant, UUID cheque, org.springframework.data.domain.Pageable page);
}
