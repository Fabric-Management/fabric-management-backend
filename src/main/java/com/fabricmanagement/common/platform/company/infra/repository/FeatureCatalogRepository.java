package com.fabricmanagement.common.platform.company.infra.repository;

import com.fabricmanagement.common.platform.company.domain.FeatureCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for FeatureCatalog entity.
 *
 * <p>Manages the master catalog of all available features in the system.</p>
 */
@Repository
public interface FeatureCatalogRepository extends JpaRepository<FeatureCatalog, UUID> {

    /**
     * Find feature by its unique feature ID.
     *
     * @param featureId the feature ID (e.g., "yarn.blend.management")
     * @return the feature if found
     */
    Optional<FeatureCatalog> findByFeatureId(String featureId);

    /**
     * Find all features for a specific OS.
     *
     * @param osCode the OS code (e.g., "YarnOS")
     * @return list of features
     */
    List<FeatureCatalog> findByOsCode(String osCode);

    /**
     * Find all features in a specific category.
     *
     * @param category the category (e.g., "Production", "Analytics")
     * @return list of features
     */
    List<FeatureCatalog> findByCategory(String category);

    /**
     * Find all active features.
     *
     * @return list of active features
     */
    List<FeatureCatalog> findByIsActiveTrue();

    /**
     * Find all features available in a specific pricing tier.
     *
     * @param tier the pricing tier name (e.g., "PROFESSIONAL")
     * @return list of features
     */
    @Query(value = "SELECT * FROM common_company.common_feature_catalog " +
                   "WHERE is_active = true " +
                   "AND (available_in_tiers IS NULL OR available_in_tiers = '[]' " +
                   "OR available_in_tiers::jsonb ? :tier)",
           nativeQuery = true)
    List<FeatureCatalog> findByTier(@Param("tier") String tier);

    /**
     * Find features by OS and tier.
     *
     * @param osCode the OS code
     * @param tier the pricing tier name
     * @return list of features
     */
    @Query(value = "SELECT * FROM common_company.common_feature_catalog " +
                   "WHERE os_code = :osCode AND is_active = true " +
                   "AND (available_in_tiers IS NULL OR available_in_tiers = '[]' " +
                   "OR available_in_tiers::jsonb ? :tier)",
           nativeQuery = true)
    List<FeatureCatalog> findByOsCodeAndTier(@Param("osCode") String osCode, @Param("tier") String tier);

    /**
     * Find features that require a specific OS.
     *
     * @param requiresOs the required OS code
     * @return list of features
     */
    List<FeatureCatalog> findByRequiresOs(String requiresOs);
}

