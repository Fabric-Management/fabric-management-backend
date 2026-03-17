package com.fabricmanagement.sales.infra.repository;

import com.fabricmanagement.sales.domain.sample.SampleDelivery;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SampleDeliveryRepository extends JpaRepository<SampleDelivery, UUID> {

  Optional<SampleDelivery> findBySampleRequestIdAndIsActiveTrue(UUID sampleRequestId);

  /** Tenant-isolated lookup — prevents cross-tenant data access. */
  Optional<SampleDelivery> findByTenantIdAndIdAndIsActiveTrue(UUID tenantId, UUID id);
}
