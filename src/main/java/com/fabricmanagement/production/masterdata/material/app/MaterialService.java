package com.fabricmanagement.production.masterdata.material.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.masterdata.material.api.facade.MaterialFacade;
import com.fabricmanagement.production.masterdata.material.domain.Material;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import com.fabricmanagement.production.masterdata.material.domain.event.MaterialCreatedEvent;
import com.fabricmanagement.production.masterdata.material.dto.CreateMaterialRequest;
import com.fabricmanagement.production.masterdata.material.dto.MaterialDto;
import com.fabricmanagement.production.masterdata.material.infra.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Material Service - Business logic for material management.
 *
 * <p>Implements MaterialFacade for cross-module communication.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialService implements MaterialFacade {

    private final MaterialRepository materialRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public MaterialDto createMaterial(CreateMaterialRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        
        log.info("Creating material: name={}, type={}", 
            request.getMaterialName(), request.getMaterialType());

        if (request.getMaterialCode() != null && 
            materialRepository.existsByTenantIdAndMaterialCode(tenantId, request.getMaterialCode())) {
            throw new IllegalArgumentException("Material code already exists");
        }

        Material material = Material.create(
            request.getMaterialType(),
            request.getUnit()
        );

        Material saved = materialRepository.save(material);

        eventPublisher.publish(new MaterialCreatedEvent(
            saved.getTenantId(),
            saved.getId(),
            saved.getMaterialType()
        ));

        log.info("✅ Material created: id={}, uid={}", saved.getId(), saved.getUid());

        return MaterialDto.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MaterialDto> findById(UUID tenantId, UUID id) {
        log.debug("Finding material: tenantId={}, id={}", tenantId, id);

        return materialRepository.findByTenantIdAndId(tenantId, id)
            .map(MaterialDto::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaterialDto> findByTenant(UUID tenantId) {
        log.debug("Finding all materials: tenantId={}", tenantId);

        return materialRepository.findByTenantIdAndIsActiveTrue(tenantId)
            .stream()
            .map(MaterialDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaterialDto> findByType(UUID tenantId, MaterialType type) {
        log.debug("Finding materials by type: tenantId={}, type={}", tenantId, type);

        return materialRepository.findByTenantIdAndMaterialTypeAndIsActiveTrue(tenantId, type)
            .stream()
            .map(MaterialDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID tenantId, UUID id) {
        return materialRepository.existsByTenantIdAndId(tenantId, id);
    }

    @Transactional
    public void deactivateMaterial(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        
        Material material = materialRepository.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new IllegalArgumentException("Material not found"));

        material.delete();
        materialRepository.save(material);

        log.info("✅ Material deactivated: id={}", id);
    }
}

