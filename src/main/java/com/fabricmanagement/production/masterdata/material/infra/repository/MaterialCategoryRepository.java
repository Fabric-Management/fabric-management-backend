package com.fabricmanagement.production.masterdata.material.infra.repository;

import com.fabricmanagement.production.masterdata.material.domain.MaterialCategory;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for MaterialCategory entity.
 */
@Repository
public interface MaterialCategoryRepository extends JpaRepository<MaterialCategory, UUID> {

    /**
     * Find all categories for tenant (system + tenant custom).
     */
    @Query("SELECT c FROM MaterialCategory c WHERE " +
           "(c.isSystemCategory = true OR c.tenantId = :tenantId) " +
           "AND c.materialType = :type " +
           "AND c.isActive = true " +
           "ORDER BY c.isSystemCategory DESC, c.displayOrder, c.categoryName")
    List<MaterialCategory> findAvailableCategories(@Param("tenantId") UUID tenantId, 
                                                   @Param("type") MaterialType type);

    /**
     * Find system categories only.
     */
    List<MaterialCategory> findByIsSystemCategoryTrueAndMaterialTypeAndIsActiveTrue(MaterialType type);

    /**
     * Find tenant custom categories.
     */
    List<MaterialCategory> findByTenantIdAndMaterialTypeAndIsActiveTrueAndIsSystemCategoryFalse(
        UUID tenantId, MaterialType type);

    /**
     * Check if category exists.
     */
    boolean existsByTenantIdAndMaterialTypeAndCategoryName(
        UUID tenantId, MaterialType type, String categoryName);
}

