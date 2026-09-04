package com.fabricmanagement.procurement.quote.infra.repository;

import com.fabricmanagement.procurement.quote.domain.SupplierQuoteLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierQuoteLineRepository extends JpaRepository<SupplierQuoteLine, UUID> {

  List<SupplierQuoteLine> findByTenantIdAndIsActiveTrue(UUID tenantId);
}
