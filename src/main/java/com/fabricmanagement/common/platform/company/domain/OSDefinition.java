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
 *   <li><b>YarnOS:</b> Yarn production management</li>
 *   <li><b>LoomOS:</b> Weaving/Loom operations</li>
 *   <li><b>DyeOS:</b> Dyeing and finishing</li>
 *   <li><b>PlanOS:</b> Production planning</li>
 *   <li><b>FinOS:</b> Financial management</li>
 *   <li><b>FabricOS:</b> Complete fabric management (all modules)</li>
 * </ul>
 *
 * <h2>Module Inclusion:</h2>
 * <p>includedModules defines which business modules are accessible:
 * <pre>
 * YarnOS includes: ["production.fiber", "production.yarn"]
 * LoomOS includes: ["production.loom", "production.weaving"]
 * FabricOS includes: ["production.*", "logistics.*", "finance.*"]
 * </pre>
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

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_tier", nullable = false, length = 20)
    @Builder.Default
    private PricingTier pricingTier = PricingTier.PROFESSIONAL;

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
}

