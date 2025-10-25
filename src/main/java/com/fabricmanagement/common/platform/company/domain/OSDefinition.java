package com.fabricmanagement.common.platform.company.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * Operating Subscription (OS) definition.
 *
 * <p>Defines available OS packages that tenants can subscribe to.
 * Each OS provides specific modules and features.</p>
 *
 * <h2>Available OS Examples:</h2>
 * <ul>
 *   <li><b>YarnOS:</b> Yarn production management (Starter, Professional, Enterprise)</li>
 *   <li><b>LoomOS:</b> Weaving/Loom operations (Starter, Professional, Enterprise)</li>
 *   <li><b>AnalyticsOS:</b> BI & Reporting (Standard, Advanced, Enterprise)</li>
 *   <li><b>IntelligenceOS:</b> AI/ML (Professional, Enterprise)</li>
 *   <li><b>FabricOS:</b> Base platform (Base tier, included for all)</li>
 * </ul>
 *
 * <h2>Module Inclusion:</h2>
 * <p>includedModules defines which business modules are accessible:
 * <pre>
 * YarnOS includes: ["production.fiber", "production.yarn"]
 * LoomOS includes: ["production.weaving"]
 * FabricOS includes: ["auth", "user", "policy", "audit", "company"]
 * </pre>
 *
 * <h2>Pricing Tiers:</h2>
 * <p>Each OS can have multiple pricing tiers with different features.
 * Tier names are defined in {@link PricingTierValidator}.</p>
 */
@Entity
@Table(name = "common_os_definition", schema = "common_company",
    indexes = {
        @Index(name = "idx_os_code", columnList = "os_code", unique = true)
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OSDefinition extends BaseEntity {

    @Column(name = "os_code", nullable = false, unique = true, length = 50)
    private String osCode;

    @Column(name = "os_name", nullable = false, length = 255)
    private String osName;

    @Enumerated(EnumType.STRING)
    @Column(name = "os_type", nullable = false, length = 20)
    @Builder.Default
    private OSType osType = OSType.FULL;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Type(JsonType.class)
    @Column(name = "included_modules", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<String> includedModules = new ArrayList<>();

    /**
     * Available pricing tiers for this OS.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>YarnOS: ["Starter", "Professional", "Enterprise"]</li>
     *   <li>AnalyticsOS: ["Standard", "Advanced", "Enterprise"]</li>
     *   <li>IntelligenceOS: ["Professional", "Enterprise"]</li>
     *   <li>FabricOS: ["Base"]</li>
     * </ul>
     */
    @Type(JsonType.class)
    @Column(name = "available_tiers", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> availableTiers = new ArrayList<>();

    /**
     * Default tier for this OS (entry-level tier).
     *
     * <p>Used when creating new subscriptions without explicit tier selection.</p>
     */
    @Column(name = "default_tier", length = 50)
    private String defaultTier;

    public boolean includesModule(String modulePath) {
        return includedModules.stream()
            .anyMatch(module -> 
                module.equals(modulePath) || 
                (module.endsWith(".*") && modulePath.startsWith(module.replace(".*", "")))
            );
    }

    public void addModule(String modulePath) {
        if (!includedModules.contains(modulePath)) {
            includedModules.add(modulePath);
        }
    }

    public void removeModule(String modulePath) {
        includedModules.remove(modulePath);
    }

    /**
     * Check if a tier is available for this OS.
     *
     * @param tierName the tier name to check
     * @return true if the tier is available
     */
    public boolean hasTier(String tierName) {
        return availableTiers != null && availableTiers.contains(tierName);
    }

    /**
     * Get the default tier for this OS.
     *
     * @return the default tier name, or null if not set
     */
    public String getDefaultTier() {
        if (defaultTier != null) {
            return defaultTier;
        }
        // Fallback to validator
        return PricingTierValidator.getDefaultTier(osCode);
    }

    /**
     * Validate OS definition before persisting.
     */
    @PrePersist
    @PreUpdate
    private void validate() {
        // Set available tiers from validator if not set
        if (availableTiers == null || availableTiers.isEmpty()) {
            this.availableTiers = new ArrayList<>(
                PricingTierValidator.getValidTiers(osCode)
            );
        }

        // Set default tier from validator if not set
        if (defaultTier == null) {
            this.defaultTier = PricingTierValidator.getDefaultTier(osCode);
        }

        // Validate default tier is in available tiers
        if (defaultTier != null && !availableTiers.contains(defaultTier)) {
            throw new IllegalStateException(
                String.format("Default tier '%s' must be in available tiers: %s",
                    defaultTier, availableTiers)
            );
        }
    }
}

