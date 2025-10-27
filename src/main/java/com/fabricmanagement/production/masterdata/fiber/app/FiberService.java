package com.fabricmanagement.production.masterdata.fiber.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.masterdata.fiber.domain.Fiber;
import com.fabricmanagement.production.masterdata.fiber.dto.CreateFiberRequest;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberDto;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fiber Service - Business logic for fiber management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiberService {

    private final FiberRepository fiberRepository;

    @Transactional
    public FiberDto createFiber(CreateFiberRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        
        log.info("Creating fiber: code={}, name={}", 
            request.getFiberCode(), request.getFiberName());

        if (fiberRepository.existsByTenantIdAndFiberCode(tenantId, request.getFiberCode())) {
            throw new IllegalArgumentException("Fiber code already exists");
        }

        // Check if material already has a fiber detail
        if (fiberRepository.findByMaterialId(request.getMaterialId()).isPresent()) {
            throw new IllegalArgumentException("Material already has fiber details");
        }

        Fiber fiber = Fiber.createPureFiber(
            request.getMaterialId(),
            request.getCategoryId(),
            request.getIsoCodeId(),
            request.getFiberCode(),
            request.getFiberName(),
            request.getFiberGrade(),
            request.getFineness(),
            request.getLengthMm(),
            request.getStrengthCndTex(),
            request.getElongationPercent()
        );
        
        fiber.setRemarks(request.getRemarks());

        Fiber saved = fiberRepository.save(fiber);

        log.info("✅ Fiber created: id={}, uid={}", saved.getId(), saved.getUid());

        return FiberDto.from(saved);
    }

    @Transactional(readOnly = true)
    public Optional<FiberDto> findById(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Finding fiber: tenantId={}, id={}", tenantId, id);

        return fiberRepository.findByTenantIdAndId(tenantId, id)
            .map(FiberDto::from);
    }

    @Transactional(readOnly = true)
    public Optional<FiberDto> findByMaterialId(UUID materialId) {
        log.debug("Finding fiber by materialId: materialId={}", materialId);

        return fiberRepository.findByMaterialId(materialId)
            .map(FiberDto::from);
    }

    @Transactional(readOnly = true)
    public List<FiberDto> findByTenant() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Finding all fibers: tenantId={}", tenantId);

        return fiberRepository.findByTenantIdAndIsActiveTrue(tenantId)
            .stream()
            .map(FiberDto::from)
            .toList();
    }

    @Transactional
    public FiberDto updateFiber(UUID id, CreateFiberRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        
        Fiber fiber = fiberRepository.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new IllegalArgumentException("Fiber not found"));

        fiber.update(
            request.getFiberName(),
            request.getFiberGrade(),
            request.getFineness(),
            request.getLengthMm(),
            request.getStrengthCndTex(),
            request.getElongationPercent(),
            request.getRemarks(),
            fiber.getStatus()
        );

        Fiber saved = fiberRepository.save(fiber);
        log.info("✅ Fiber updated: id={}", saved.getId());

        return FiberDto.from(saved);
    }

    @Transactional
    public void deactivateFiber(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        
        Fiber fiber = fiberRepository.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new IllegalArgumentException("Fiber not found"));

        fiber.delete();
        fiberRepository.save(fiber);

        log.info("✅ Fiber deactivated: id={}", id);
    }
}
