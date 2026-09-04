package com.fabricmanagement.procurement.rfq.infra.repository;

import com.fabricmanagement.procurement.rfq.domain.SupplierRFQLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRFQLineRepository extends JpaRepository<SupplierRFQLine, UUID> {

  List<SupplierRFQLine> findByTenantIdAndIsActiveTrue(UUID tenantId);
}
