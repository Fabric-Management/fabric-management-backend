package com.fabricmanagement.production.masterdata.fiber.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberIsoCode;
import com.fabricmanagement.production.masterdata.fiber.dto.CreateNewFiberTypeRequest;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberTypeDto;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberIsoCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing fiber types (platform-level ISO codes).
 *
 * <p>Tenants can:
 * <ul>
 *   <li>Create new fiber types (create-only, strict validation)</li>
 *   <li>View all active fiber types (platform + tenant creations)</li>
 * </ul>
 *
 * <p>Tenants CANNOT:
 * <ul>
 *   <li>Update fiber types</li>
 *   <li>Delete fiber types</li>
 *   <li>Modify platform-level fiber types (tenant_id = SYSTEM_TENANT_ID)</li>
 * </ul>
 *
 * <p>Platform admins can modify all fiber types.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiberTypeService {

    private final FiberIsoCodeRepository fiberIsoCodeRepository;
    private final FiberTypeValidationService validationService;

    /**
     * Create a new 100% fiber type (platform-level).
     *
     * <p><b>CREATE-ONLY:</b> Tenant users can create but cannot update/delete.</p>
     * <p>Once created, accessible by ALL tenants.</p>
     *
     * @param request Request with fiber type details
     * @return Created fiber type
     */
    @Transactional
    public FiberTypeDto createNewFiberType(CreateNewFiberTypeRequest request) {
        log.info("Creating new fiber type: isoCode={}, fiberName={}", 
            request.getIsoCode(), request.getFiberName());

        // CRITICAL VALIDATION: Check if ISO code already exists
        if (fiberIsoCodeRepository.existsByIsoCode(request.getIsoCode())) {
            throw new IllegalArgumentException(
                String.format("Fiber type with ISO code '%s' already exists. Please contact platform admin if you believe this is a typo.", 
                    request.getIsoCode()));
        }

        // Validate ISO code format (uppercase, alphanumeric, 2-10 chars)
        validationService.validateIsoCodeFormat(request.getIsoCode());

        // Validate fiber name uniqueness
        validationService.validateFiberNameUniqueness(request.getFiberName());

        // Create fiber type with tenant_id = SYSTEM_TENANT_ID (platform-level)
        // BUT set created_by = actual tenant user for audit
        FiberIsoCode fiberType = FiberIsoCode.builder()
            .categoryId(request.getCategoryId())
            .isoCode(request.getIsoCode())
            .fiberName(request.getFiberName())
            .fiberType(request.getFiberType())
            .description(request.getDescription())
            .isOfficialIso(request.getIsOfficialIso())
            .displayOrder(request.getDisplayOrder())
            .build();
        
        fiberType.setIsActive(true);

        FiberIsoCode saved = fiberIsoCodeRepository.save(fiberType);

        log.info("✅ New fiber type created: id={}, isoCode={}, accessible by all tenants", 
            saved.getId(), saved.getIsoCode());

        return FiberTypeDto.from(saved);
    }

    /**
     * Get all active fiber types (platform + tenant creations).
     *
     * @return List of active fiber types
     */
    public List<FiberTypeDto> getAllActiveFiberTypes() {
        List<FiberIsoCode> fiberTypes = fiberIsoCodeRepository.findAllActive();
        return fiberTypes.stream()
            .map(FiberTypeDto::from)
            .collect(Collectors.toList());
    }
}

