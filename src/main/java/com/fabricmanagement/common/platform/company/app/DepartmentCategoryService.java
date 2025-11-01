package com.fabricmanagement.common.platform.company.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.company.domain.DepartmentCategory;
import com.fabricmanagement.common.platform.company.infra.repository.DepartmentCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Department Category Service - Business logic for department category management.
 *
 * <p>Handles categorization of departments (Production, Administrative, Utility, etc.)</p>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Category CRUD with tenant isolation</li>
 *   <li>Category name uniqueness validation</li>
 *   <li>Display order management</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DepartmentCategoryService {

    private final DepartmentCategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<DepartmentCategory> findAll() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Finding all department categories: tenantId={}", tenantId);

        return categoryRepository.findByTenantIdAndIsActiveTrueOrderByDisplayOrderAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public Optional<DepartmentCategory> findById(UUID categoryId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Finding department category: tenantId={}, categoryId={}", tenantId, categoryId);

        return categoryRepository.findByTenantIdAndId(tenantId, categoryId);
    }

    @Transactional(readOnly = true)
    public Optional<DepartmentCategory> findByName(String categoryName) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Finding department category by name: tenantId={}, name={}", tenantId, categoryName);

        return categoryRepository.findByTenantIdAndCategoryName(tenantId, categoryName);
    }

    @Transactional
    public DepartmentCategory create(String categoryName, String description, Integer displayOrder) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Creating department category: tenantId={}, categoryName={}", tenantId, categoryName);

        if (categoryRepository.existsByTenantIdAndCategoryName(tenantId, categoryName)) {
            throw new IllegalArgumentException("Category with name '" + categoryName + "' already exists");
        }

        DepartmentCategory category = DepartmentCategory.create(categoryName, description, displayOrder);
        DepartmentCategory saved = categoryRepository.save(category);

        log.info("Department category created: id={}, uid={}, name={}", saved.getId(), saved.getUid(), saved.getCategoryName());

        return saved;
    }

    @Transactional
    public DepartmentCategory update(UUID categoryId, String categoryName, String description, Integer displayOrder) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Updating department category: tenantId={}, categoryId={}", tenantId, categoryId);

        DepartmentCategory category = categoryRepository.findByTenantIdAndId(tenantId, categoryId)
            .orElseThrow(() -> new IllegalArgumentException("Department category not found"));

        category.setCategoryName(categoryName);
        category.setDescription(description);
        category.setDisplayOrder(displayOrder);

        DepartmentCategory saved = categoryRepository.save(category);

        log.info("Department category updated: id={}, name={}", saved.getId(), saved.getCategoryName());

        return saved;
    }

    @Transactional
    public void deactivate(UUID categoryId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Deactivating department category: tenantId={}, categoryId={}", tenantId, categoryId);

        DepartmentCategory category = categoryRepository.findByTenantIdAndId(tenantId, categoryId)
            .orElseThrow(() -> new IllegalArgumentException("Department category not found"));

        category.delete();
        categoryRepository.save(category);

        log.warn("Department category deactivated: id={}, name={}", category.getId(), category.getCategoryName());
    }
}

