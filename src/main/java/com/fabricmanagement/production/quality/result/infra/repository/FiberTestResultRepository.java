package com.fabricmanagement.production.quality.result.infra.repository;

import com.fabricmanagement.production.quality.result.domain.FiberTestResult;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FiberTestResultRepository extends JpaRepository<FiberTestResult, UUID> {

  List<FiberTestResult> findByTenantIdAndIsActiveTrue(UUID tenantId);

  Optional<FiberTestResult> findByTenantIdAndId(UUID tenantId, UUID id);

  List<FiberTestResult> findByTenantIdAndFiberBatchId(UUID tenantId, UUID fiberBatchId);

  List<FiberTestResult> findByTenantIdAndFiberBatchIdAndIsActiveTrue(
      UUID tenantId, UUID fiberBatchId);

  List<FiberTestResult> findByTenantIdAndApprovalStatus(
      UUID tenantId, TestApprovalStatus approvalStatus);

  boolean existsByTenantIdAndFiberBatchIdAndApprovalStatus(
      UUID tenantId, UUID fiberBatchId, TestApprovalStatus approvalStatus);
}
