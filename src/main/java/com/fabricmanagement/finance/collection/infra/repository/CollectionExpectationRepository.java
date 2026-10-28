package com.fabricmanagement.finance.collection.infra.repository;

import com.fabricmanagement.finance.collection.domain.CollectionExpectation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionExpectationRepository
    extends JpaRepository<CollectionExpectation, UUID> {
  @org.springframework.data.jpa.repository.Query(
      "select e.obligationId from CollectionExpectation e where e.tenantId=:tenant and"
          + " e.revisionId=:revision and e.obligationId in :ids")
  java.util.Set<UUID> recordedObligations(UUID tenant, UUID revision, java.util.List<UUID> ids);

  org.springframework.data.domain.Page<CollectionExpectation> findByTenantIdAndObligationId(
      UUID tenant, UUID obligation, org.springframework.data.domain.Pageable page);
}
