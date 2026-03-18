package com.fabricmanagement.procurement.rfq.infra.repository;

import com.fabricmanagement.procurement.rfq.domain.SupplierRFQRecipient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierRFQRecipientRepository extends JpaRepository<SupplierRFQRecipient, UUID> {
  Optional<SupplierRFQRecipient> findByTenantIdAndIdAndIsActiveTrue(UUID tenantId, UUID id);
}
