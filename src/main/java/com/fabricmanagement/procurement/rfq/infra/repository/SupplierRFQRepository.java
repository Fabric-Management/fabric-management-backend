package com.fabricmanagement.procurement.rfq.infra.repository;

import com.fabricmanagement.procurement.rfq.domain.SupplierRFQ;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierRFQRepository
    extends JpaRepository<SupplierRFQ, UUID>, JpaSpecificationExecutor<SupplierRFQ> {
  Optional<SupplierRFQ> findByTenantIdAndIdAndIsActiveTrue(UUID tenantId, UUID id);

  @EntityGraph(attributePaths = "lines")
  List<SupplierRFQ> findAllByTenantIdAndIdInAndIsActiveTrue(UUID tenantId, Collection<UUID> ids);
}
