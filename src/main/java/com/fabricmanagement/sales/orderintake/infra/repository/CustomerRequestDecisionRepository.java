package com.fabricmanagement.sales.orderintake.infra.repository;

import com.fabricmanagement.sales.orderintake.domain.CustomerRequestDecision;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRequestDecisionRepository
    extends JpaRepository<CustomerRequestDecision, UUID> {

  List<CustomerRequestDecision> findByTenantIdAndRequestIdOrderByRecordedAtDescIdDesc(
      UUID tenantId, UUID requestId);
}
