package com.fabricmanagement.production.masterdata.fiber.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.masterdata.fiber.api.facade.FiberFacade;
import com.fabricmanagement.production.masterdata.fiber.domain.Fiber;
import com.fabricmanagement.production.masterdata.fiber.domain.event.FiberCreatedEvent;
import com.fabricmanagement.production.masterdata.fiber.dto.CreateBlendedFiberRequest;
import com.fabricmanagement.production.masterdata.fiber.dto.CreateFiberRequest;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberDto;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fiber Service - Business logic for fiber management.
 *
 * <p>Implements FiberFacade for cross-module communication.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiberService implements FiberFacade {

    private final FiberRepository fiberRepository;
    private final DomainEventPublisher eventPublisher;
    private final FiberCompositionService compositionService;
    private final FiberValidationService validationService;

    @Transactional
    public FiberDto createFiber(CreateFiberRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        
        log.info("Creating fiber: name={}", request.getFiberName());

        // Check if this is a pure 100% fiber being recreated
        if (request.getFiberIsoCodeId() != null) {
            throw new IllegalArgumentException(
                "Pure 100% fibers are pre-defined by the system. " +
                "You can only create blended fibers (combinations of existing fibers). " +
                "If you need a custom fiber, please use the default fiber types available.");
        }


        // Check if material already has a fiber detail
        if (fiberRepository.findByMaterialId(request.getMaterialId()).isPresent()) {
            throw new IllegalArgumentException("Material already has fiber details. Each material can only have one fiber.");
        }

        Fiber fiber = Fiber.createPureFiber(
            request.getMaterialId(),
            request.getFiberCategoryId(),
            request.getFiberIsoCodeId(),
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

        // Publish domain event
        eventPublisher.publish(new FiberCreatedEvent(
            saved.getTenantId(),
            saved.getId(),
            saved.getFiberCode(),
            saved.getFiberName(),
            saved.getFiberCategoryId(),
            saved.getFiberIsoCodeId()
        ));

        log.info("✅ Fiber created: id={}, uid={}", saved.getId(), saved.getUid());

        return FiberDto.from(saved);
    }

    /**
     * Create a blended fiber (e.g., 60% Cotton + 40% Viscose).
     *
     * <p>This creates a new fiber and sets its composition automatically.</p>
     *
     * @param request Blended fiber request with composition map
     * @return Created blended fiber
     */
    @Transactional
    public FiberDto createBlendedFiber(CreateBlendedFiberRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        
        log.info("Creating blended fiber: code={}, name={}", 
            request.getFiberCode(), request.getFiberName());

        if (fiberRepository.existsByTenantIdAndFiberCode(tenantId, request.getFiberCode())) {
            throw new IllegalArgumentException("Fiber code already exists");
        }

        if (fiberRepository.findByMaterialId(request.getMaterialId()).isPresent()) {
            throw new IllegalArgumentException("Material already has fiber details");
        }

        // Validate fiber code format
        validationService.validateFiberCodeFormat(request.getFiberCode());

        // Validate composition percentages
        validationService.validateCompositionPercentages(request.getComposition());

        // Validate minimum ratio (no fiber less than 5%)
        validationService.validateMinimumRatio(request.getComposition(), 5.0);

        // Validate maximum components (max 5 fibers in a blend)
        validationService.validateMaxComponents(request.getComposition(), 5);

        // Validate base fibers exist and are active
        Map<UUID, String> baseFiberNames = new HashMap<>();
        for (UUID baseFiberId : request.getComposition().keySet()) {
            Fiber baseFiber = fiberRepository.findById(baseFiberId)
                .orElseThrow(() -> new IllegalArgumentException("Base fiber not found: " + baseFiberId));
            baseFiberNames.put(baseFiberId, baseFiber.getFiberName());
        }

        // Validate base fibers are active
        validationService.validateBaseFibersActive(request.getComposition());

        // Check if a fiber with identical composition already exists
        if (isDuplicateComposition(request.getComposition())) {
            throw new IllegalArgumentException(
                "A fiber with this exact composition already exists. Cannot create duplicate blend.");
        }

        // Validate circular references
        validationService.validateNoCircularReferences(request.getComposition());

        // Validate tenant consistency
        UUID currentTenantId = TenantContext.getCurrentTenantId();
        validationService.validateTenantConsistency(request.getComposition(), currentTenantId);

        // Generate suggested name if not provided
        String fiberName = request.getFiberName();
        if (fiberName == null || fiberName.isBlank()) {
            fiberName = generateFiberName(request.getComposition(), baseFiberNames);
            log.info("Generated fiber name: {}", fiberName);
        }

        // Create blended fiber
        Fiber blendedFiber = Fiber.createBlendedFiber(
            request.getMaterialId(),
            request.getFiberCategoryId(),
            request.getFiberIsoCodeId(),
            request.getFiberCode(),
            fiberName,  // Use generated or provided name
            request.getFiberGrade()
        );
        
        blendedFiber.setRemarks(request.getRemarks());
        Fiber saved = fiberRepository.save(blendedFiber);

        // Set composition
        compositionService.setComposition(saved.getId(), request.getComposition());

        // Publish domain event
        eventPublisher.publish(new FiberCreatedEvent(
            saved.getTenantId(),
            saved.getId(),
            saved.getFiberCode(),
            saved.getFiberName(),
            saved.getFiberCategoryId(),
            saved.getFiberIsoCodeId()
        ));

        log.info("✅ Blended fiber created: id={}, uid={}", saved.getId(), saved.getUid());

        return FiberDto.from(saved);
    }

    @Transactional(readOnly = true)
    public Optional<FiberDto> getById(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Getting fiber: tenantId={}, id={}", tenantId, id);

        return fiberRepository.findByTenantIdAndId(tenantId, id)
            .map(FiberDto::from);
    }

    @Transactional(readOnly = true)
    public Optional<FiberDto> getByMaterialId(UUID materialId) {
        log.debug("Getting fiber by materialId: materialId={}", materialId);

        return fiberRepository.findByMaterialId(materialId)
            .map(FiberDto::from);
    }

    @Transactional(readOnly = true)
    public List<FiberDto> getAll() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Getting all fibers: tenantId={}", tenantId);

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

    // =====================================================
    // FiberFacade Implementation
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public Optional<FiberDto> findById(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("FiberFacade: Finding fiber: tenantId={}, id={}", tenantId, id);
        return fiberRepository.findByTenantIdAndId(tenantId, id)
            .map(FiberDto::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FiberDto> findByMaterialId(UUID materialId) {
        log.debug("FiberFacade: Finding fiber by materialId: materialId={}", materialId);
        return fiberRepository.findByMaterialId(materialId)
            .map(FiberDto::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FiberDto> findAll() {
        return getAll();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return fiberRepository.findByTenantIdAndId(tenantId, id).isPresent();
    }

    /**
     * Check if a fiber with identical composition already exists.
     * 
     * <p>Compares compositions by base fiber IDs and percentages.</p>
     * 
     * @param composition Map of baseFiberId → percentage
     * @return true if duplicate exists
     */
    private boolean isDuplicateComposition(Map<UUID, BigDecimal> composition) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        
        // Get all blended fibers for this tenant
        List<Fiber> allFibers = fiberRepository.findByTenantIdAndIsActiveTrue(tenantId);
        
        for (Fiber fiber : allFibers) {
            Map<UUID, BigDecimal> existingComposition = compositionService.getComposition(fiber.getId());
            
            // Check if composition maps are identical
            if (compositionsMatch(composition, existingComposition)) {
                log.warn("Duplicate composition found: fiber={}, composition={}", fiber.getFiberCode(), existingComposition);
                return true;
            }
        }
        
        return false;
    }

    /**
     * Compare two compositions to check if they match exactly.
     * 
     * @param composition1 First composition
     * @param composition2 Second composition
     * @return true if compositions are identical
     */
    private boolean compositionsMatch(Map<UUID, BigDecimal> composition1, Map<UUID, BigDecimal> composition2) {
        // Empty compositions don't match
        if (composition1.isEmpty() && composition2.isEmpty()) {
            return false;
        }
        
        // Different sizes can't match
        if (composition1.size() != composition2.size()) {
            return false;
        }
        
        // Compare each entry
        for (Map.Entry<UUID, BigDecimal> entry : composition1.entrySet()) {
            BigDecimal percentage2 = composition2.get(entry.getKey());
            
            if (percentage2 == null) {
                return false;
            }
            
            // Compare percentages with 0.01 tolerance
            if (Math.abs(entry.getValue().subtract(percentage2).doubleValue()) > 0.01) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Generate a suggested name for a blended fiber based on composition.
     * 
     * <p>Example: 60% Cotton + 40% Viscose → "Cotton 60% Viscose 40% Blend"</p>
     * 
     * @param composition Map of baseFiberId → percentage
     * @param baseFiberNames Map of baseFiberId → fiberName
     * @return Suggested fiber name
     */
    private String generateFiberName(Map<UUID, BigDecimal> composition, Map<UUID, String> baseFiberNames) {
        return composition.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue())) // Sort by percentage descending
            .map(entry -> {
                String fiberName = baseFiberNames.get(entry.getKey());
                BigDecimal percentage = entry.getValue();
                return String.format("%s %.0f%%", fiberName, percentage);
            })
            .collect(Collectors.joining(" + ")) + " Blend";
    }
}
