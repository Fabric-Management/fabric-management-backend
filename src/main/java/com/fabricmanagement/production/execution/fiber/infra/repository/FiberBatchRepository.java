package com.fabricmanagement.production.execution.fiber.infra.repository;

import com.fabricmanagement.production.execution.fiber.domain.FiberBatch;
import com.fabricmanagement.production.execution.fiber.domain.FiberBatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FiberBatchRepository extends JpaRepository<FiberBatch, UUID> {
    
    List<FiberBatch> findByTenantId(UUID tenantId);
    
    List<FiberBatch> findByTenantIdAndIsActiveTrue(UUID tenantId);
    
    Optional<FiberBatch> findByTenantIdAndId(UUID tenantId, UUID id);
    
    List<FiberBatch> findByTenantIdAndFiberId(UUID tenantId, UUID fiberId);
    
    List<FiberBatch> findByTenantIdAndFiberIdAndIsActiveTrue(UUID tenantId, UUID fiberId);
    
    Optional<FiberBatch> findByTenantIdAndBatchCode(UUID tenantId, String batchCode);
    
    List<FiberBatch> findByTenantIdAndStatus(UUID tenantId, FiberBatchStatus status);
}

