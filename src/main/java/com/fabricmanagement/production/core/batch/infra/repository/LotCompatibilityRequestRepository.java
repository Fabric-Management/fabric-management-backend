package com.fabricmanagement.production.core.batch.infra.repository;

import com.fabricmanagement.production.core.batch.domain.LotCompatibilityRequest;
import com.fabricmanagement.production.core.batch.domain.LotCompatibilityRequestStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LotCompatibilityRequestRepository
    extends JpaRepository<LotCompatibilityRequest, UUID> {

  Optional<LotCompatibilityRequest> findFirstByTenantIdAndSourceIdAndBatchKeyAndStatus(
      UUID tenantId, UUID sourceId, String batchKey, LotCompatibilityRequestStatus status);

  List<LotCompatibilityRequest> findByTenantIdAndStatusOrderByRequestedAtAscIdAsc(
      UUID tenantId, LotCompatibilityRequestStatus status);

  List<LotCompatibilityRequest> findByTenantIdAndSourceIdOrderByRequestedAtDescIdDesc(
      UUID tenantId, UUID sourceId);

  Optional<LotCompatibilityRequest> findByTenantIdAndId(UUID tenantId, UUID id);
}
