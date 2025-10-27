package com.fabricmanagement.production.masterdata.material.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import com.fabricmanagement.production.masterdata.material.dto.MaterialCategoryDto;
import com.fabricmanagement.production.masterdata.material.infra.repository.MaterialCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for Material Category management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialCategoryService {

    private final MaterialCategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<MaterialCategoryDto> findByType(MaterialType type) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Finding categories: tenantId={}, type={}", tenantId, type);

        return categoryRepository.findAvailableCategories(tenantId, type)
            .stream()
            .map(MaterialCategoryDto::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<MaterialCategoryDto> findAllByType(MaterialType type) {
        log.debug("Finding all categories for type: {}", type);

        return categoryRepository.findByIsSystemCategoryTrueAndMaterialTypeAndIsActiveTrue(type)
            .stream()
            .map(MaterialCategoryDto::from)
            .toList();
    }
}
