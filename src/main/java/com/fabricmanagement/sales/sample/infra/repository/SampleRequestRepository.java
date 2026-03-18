package com.fabricmanagement.sales.sample.infra.repository;

import com.fabricmanagement.sales.sample.domain.SampleRequest;
import com.fabricmanagement.sales.sample.domain.SampleRequestStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SampleRequestRepository extends JpaRepository<SampleRequest, UUID> {

  Optional<SampleRequest> findByTenantIdAndIdAndIsActiveTrue(UUID tenantId, UUID id);

  List<SampleRequest> findAllByTenantIdAndCustomerIdAndIsActiveTrue(UUID tenantId, UUID customerId);

  @Query(
      """
      SELECT sr FROM SampleRequest sr
      WHERE sr.tenantId = :tenantId
        AND sr.status = :status
        AND sr.isActive = true
      ORDER BY sr.createdAt DESC
      """)
  List<SampleRequest> findByStatus(
      @Param("tenantId") UUID tenantId, @Param("status") SampleRequestStatus status);
}
