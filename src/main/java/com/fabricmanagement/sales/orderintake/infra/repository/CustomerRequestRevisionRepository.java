package com.fabricmanagement.sales.orderintake.infra.repository;

import com.fabricmanagement.sales.orderintake.domain.CustomerRequestRevision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRequestRevisionRepository
    extends JpaRepository<CustomerRequestRevision, UUID> {

  List<CustomerRequestRevision> findByTenantIdAndRequestIdOrderByRevisionNoDesc(
      UUID tenantId, UUID requestId);

  Optional<CustomerRequestRevision> findByTenantIdAndRequestIdAndRevisionNo(
      UUID tenantId, UUID requestId, int revisionNo);
}
