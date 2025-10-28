package com.fabricmanagement.production.execution.fiber.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.fiber.domain.FiberBatch;
import com.fabricmanagement.production.execution.fiber.dto.CreateFiberBatchRequest;
import com.fabricmanagement.production.execution.fiber.dto.FiberBatchDto;
import com.fabricmanagement.production.execution.fiber.infra.repository.FiberBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing fiber batches.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiberBatchService {
    
    private final FiberBatchRepository fiberBatchRepository;
    
    @Transactional
    public FiberBatchDto create(CreateFiberBatchRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Creating fiber batch: tenantId={}, request={}", tenantId, request);
        
        if (fiberBatchRepository.findByTenantIdAndBatchCode(tenantId, request.getBatchCode()).isPresent()) {
            throw new IllegalArgumentException("Batch code already exists");
        }
        
        FiberBatch batch = FiberBatch.create(
            tenantId,
            request.getFiberId(),
            request.getBatchCode(),
            request.getSupplierBatchCode(),
            request.getQuantity(),
            request.getUnit(),
            request.getProductionDate() != null ? request.getProductionDate() : Instant.now(),
            request.getExpiryDate(),
            request.getWarehouseLocation(),
            request.getRemarks()
        );
        
        batch = fiberBatchRepository.save(batch);
        log.info("Created fiber batch: id={}, batchCode={}", batch.getId(), batch.getBatchCode());
        
        return FiberBatchDto.from(batch);
    }
    
    @Transactional(readOnly = true)
    public List<FiberBatchDto> getAll() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Getting all fiber batches: tenantId={}", tenantId);
        
        return fiberBatchRepository.findByTenantIdAndIsActiveTrue(tenantId)
            .stream()
            .map(FiberBatchDto::from)
            .toList();
    }
    
    @Transactional(readOnly = true)
    public Optional<FiberBatchDto> getById(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Getting fiber batch: tenantId={}, id={}", tenantId, id);
        
        return fiberBatchRepository.findById(id)
            .filter(batch -> batch.getTenantId().equals(tenantId))
            .map(FiberBatchDto::from);
    }
    
    @Transactional(readOnly = true)
    public List<FiberBatchDto> getByFiberId(UUID fiberId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Getting fiber batches by fiberId: tenantId={}, fiberId={}", tenantId, fiberId);
        
        return fiberBatchRepository.findByTenantIdAndFiberIdAndIsActiveTrue(tenantId, fiberId)
            .stream()
            .map(FiberBatchDto::from)
            .toList();
    }
}

