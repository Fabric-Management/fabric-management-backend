package com.fabricmanagement.procurement.rfq.infra.repository;

import com.fabricmanagement.procurement.rfq.domain.SupplierRFQ;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierRFQRepository extends JpaRepository<SupplierRFQ, UUID> {
  Optional<SupplierRFQ> findByTenantIdAndIdAndIsActiveTrue(UUID tenantId, UUID id);
}
