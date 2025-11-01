package com.fabricmanagement.production.execution.fiber.infra.repository;

import com.fabricmanagement.production.execution.fiber.domain.FiberBatch;
import com.fabricmanagement.production.execution.fiber.domain.FiberBatchStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FiberBatchRepository extends JpaRepository<FiberBatch, UUID> {
    
    List<FiberBatch> findByTenantId(UUID tenantId);
    
    List<FiberBatch> findByTenantIdAndIsActiveTrue(UUID tenantId);
    
    @Lock(LockModeType.OPTIMISTIC)
    Optional<FiberBatch> findByIdAndTenantId(UUID id, UUID tenantId);
    
    List<FiberBatch> findByTenantIdAndFiberId(UUID tenantId, UUID fiberId);
    
    List<FiberBatch> findByTenantIdAndFiberIdAndIsActiveTrue(UUID tenantId, UUID fiberId);
    
    Optional<FiberBatch> findByTenantIdAndBatchCode(UUID tenantId, String batchCode);
    
    boolean existsByTenantIdAndBatchCode(UUID tenantId, String batchCode);
    
    List<FiberBatch> findByTenantIdAndStatus(UUID tenantId, FiberBatchStatus status);
    
    List<FiberBatch> findByTenantIdAndFiberIdAndStatusIn(
        UUID tenantId, UUID fiberId, Collection<FiberBatchStatus> statuses);
}

