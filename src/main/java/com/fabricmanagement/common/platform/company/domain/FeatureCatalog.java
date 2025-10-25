package com.fabricmanagement.common.platform.company.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.List;

/**
 * Feature catalog - Master list of all available features.
 *
 * <p>Defines which features exist in the system, which OS they belong to,
 * and which pricing tiers include them. This is used for:</p>
 * <ul>
 *   <li>Feature entitlement checks (Layer 2 of Policy Engine)</li>
 *   <li>Admin UI for subscription management</li>
 *   <li>Customer-facing pricing pages</li>
 *   <li>Feature discovery and documentation</li>
 * </ul>
 *
 * <h2>Feature ID Convention:</h2>
 * <pre>
 * {os_prefix}.{module}.{feature_name}
 *
 * Examples:
 *   yarn.fiber.create              → Create fiber entities (YarnOS)
 *   yarn.blend.management          → Blend management (YarnOS Professional+)
 *   weaving.loom.management        → Loom management (LoomOS)
 *   intelligence.demand.forecasting → AI forecasting (IntelligenceOS)
 * </pre>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * FeatureCatalog blendFeature = FeatureCatalog.builder()
 *     .featureId("yarn.blend.management")
 *     .osCode("YarnOS")
 *     .featureName("Blend Management")
 *     .description("Create and manage fiber blends with custom ratios")
 *     .availableInTiers(List.of("PROFESSIONAL", "ENTERPRISE"))
 *     .build();
 * }</pre>
 */
@Entity
@Table(name = "common_feature_catalog", schema = "common_company",
    indexes = {
        @Index(name = "idx_feature_os_code", columnList = "os_code"),
        @Index(name = "idx_feature_id", columnList = "feature_id", unique = true)
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureCatalog extends BaseEntity {

    /**
     * Unique feature identifier.
     *
     * <p>Format: {os_prefix}.{module}.{feature_name}</p>
     * <p>Example: "yarn.blend.management"</p>
     */
    @Column(name = "feature_id", unique = true, nullable = false, length = 100)
    private String featureId;

    /**
     * OS this feature belongs to.
     *
     * <p>Examples: "YarnOS", "LoomOS", "FabricOS"</p>
     */
    @Column(name = "os_code", nullable = false, length = 50)
    private String osCode;

    /**
     * Human-readable feature name.
     *
     * <p>Example: "Blend Management"</p>
     */
    @Column(name = "feature_name", nullable = false, length = 255)
    private String featureName;

    /**
     * Detailed description of what this feature does.
     *
     * <p>Used in admin UI and customer-facing documentation.</p>
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Pricing tiers that include this feature.
     *
     * <p>Array of tier names: ["PROFESSIONAL", "ENTERPRISE"]</p>
     * <p>Empty array means feature is available in all tiers.</p>
     */
    @Type(JsonType.class)
    @Column(name = "available_in_tiers", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> availableInTiers = List.of("ENTERPRISE");

    /**
     * Feature category for grouping in UI.
     *
     * <p>Examples: "Production", "Analytics", "Integration", "Core"</p>
     */
    @Column(name = "category", length = 50)
    private String category;

    /**
     * Whether this feature is currently active/available.
     *
     * <p>Can be used to soft-deprecate features or enable beta features.</p>
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Whether this feature requires another OS.
     *
     * <p>Example: "yarn.iot.sensors" requires EdgeOS to be active</p>
     */
    @Column(name = "requires_os")
    private String requiresOs;

    // ==================== Business Logic ====================

    /**
     * Check if feature is available in the given pricing tier.
     *
     * @param tierName the pricing tier name (e.g., "Professional", "Standard")
     * @return true if feature is included in this tier
     */
    public boolean isAvailableInTier(String tierName) {
        if (availableInTiers == null || availableInTiers.isEmpty()) {
            // Empty means available in all tiers
            return true;
        }
        return availableInTiers.contains(tierName);
    }

    /**
     * Check if feature requires an additional OS subscription.
     *
     * @return true if requiresOs is set
     */
    public boolean hasOsDependency() {
        return requiresOs != null && !requiresOs.isBlank();
    }

    /**
     * Get the minimum tier that includes this feature.
     *
     * <p>Returns the "lowest" tier name from availableInTiers.
     * For YarnOS/LoomOS/etc: Starter < Professional < Enterprise
     * For AnalyticsOS/AccountOS: Standard < Advanced/Professional < Enterprise</p>
     *
     * @return the minimum tier name, or null if available in all tiers
     */
    public String getMinimumTier() {
        if (availableInTiers == null || availableInTiers.isEmpty()) {
            return null; // Available in all tiers
        }

        // Check in order of tier hierarchy
        if (availableInTiers.contains("Starter") || availableInTiers.contains("Standard")) {
            return availableInTiers.stream()
                .filter(t -> t.equals("Starter") || t.equals("Standard"))
                .findFirst()
                .orElse(null);
        }
        if (availableInTiers.contains("Professional") || availableInTiers.contains("Advanced")) {
            return availableInTiers.stream()
                .filter(t -> t.equals("Professional") || t.equals("Advanced"))
                .findFirst()
                .orElse(null);
        }
        if (availableInTiers.contains("Enterprise")) {
            return "Enterprise";
        }

        // Fallback: return first tier
        return availableInTiers.isEmpty() ? null : availableInTiers.get(0);
    }

    /**
     * Extract OS prefix from feature ID.
     *
     * @return the OS prefix (e.g., "yarn" from "yarn.blend.management")
     */
    public String getOsPrefix() {
        if (featureId == null || !featureId.contains(".")) {
            return null;
        }
        return featureId.substring(0, featureId.indexOf("."));
    }

    /**
     * Extract module name from feature ID.
     *
     * @return the module name (e.g., "blend" from "yarn.blend.management")
     */
    public String getModuleName() {
        if (featureId == null) {
            return null;
        }
        String[] parts = featureId.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        return parts[1];
    }

    /**
     * Validate feature before persisting.
     */
    @PrePersist
    @PreUpdate
    private void validate() {
        if (featureId == null || featureId.isBlank()) {
            throw new IllegalStateException("Feature ID is required");
        }

        String[] parts = featureId.split("\\.");
        if (parts.length < 2) {
            throw new IllegalStateException(
                "Feature ID must follow format: {os}.{module}.{feature}"
            );
        }

        if (osCode == null || osCode.isBlank()) {
            throw new IllegalStateException("OS code is required");
        }
    }

    @Override
    protected String getModuleCode() {
        return "FEAT";
    }
}

