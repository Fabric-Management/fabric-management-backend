package com.fabricmanagement.sales.orderintake.infra.repository;

import com.fabricmanagement.sales.orderintake.domain.CustomerRequestEvaluation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRequestEvaluationRepository
    extends JpaRepository<CustomerRequestEvaluation, UUID> {

  List<CustomerRequestEvaluation> findByTenantIdAndRequestIdOrderByEvaluatedAtDescIdDesc(
      UUID tenantId, UUID requestId);
}
