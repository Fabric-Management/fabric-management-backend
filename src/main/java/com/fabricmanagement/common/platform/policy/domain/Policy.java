package com.fabricmanagement.common.platform.policy.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.HashMap;
import java.util.Map;

/**
 * Policy entity - Access control rule.
 *
 * <p>Defines who can do what under which conditions.
 * This is Layer 4 of the 4-Layer Access Control Architecture.</p>
 *
 * <h2>Policy Layers (for context):</h2>
 * <pre>
 * Layer 1: OS Subscription (handled by EnhancedSubscriptionService)
 * Layer 2: Feature Entitlement (handled by EnhancedSubscriptionService)
 * Layer 3: Usage Quota (handled by EnhancedSubscriptionService)
 * Layer 4: RBAC/ABAC (THIS MODULE!) ← User roles, permissions, conditions
 * </pre>
 *
 * <h2>Policy Structure:</h2>
 * <pre>{@code
 * Policy policy = Policy.builder()
 *     .policyId("fabric.yarn.create")
 *     .resource("fabric.yarn")
 *     .action("create")
 *     .effect(PolicyEffect.ALLOW)
 *     .priority(100)
 *     .conditions(Map.of(
 *         "roles", List.of("PLANNER", "ADMIN"),
 *         "departments", List.of("production"),
 *         "timeRange", Map.of("start", "08:00", "end", "18:00")
 *     ))
 *     .build();
 * }</pre>
 */
@Entity
@Table(name = "common_policy", schema = "common_policy",
    indexes = {
        @Index(name = "idx_policy_id", columnList = "policy_id", unique = true),
        @Index(name = "idx_policy_resource", columnList = "resource"),
        @Index(name = "idx_policy_priority", columnList = "priority DESC")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Policy extends BaseEntity {

    @Column(name = "policy_id", nullable = false, unique = true, length = 100)
    private String policyId;

    @Column(name = "resource", nullable = false, length = 100)
    private String resource;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 100;

    @Enumerated(EnumType.STRING)
    @Column(name = "effect", nullable = false, length = 10)
    @Builder.Default
    private PolicyEffect effect = PolicyEffect.DENY;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Type(JsonType.class)
    @Column(name = "conditions", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> conditions = new HashMap<>();

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    public static Policy createAllowPolicy(String policyId, String resource, String action, 
                                          Map<String, Object> conditions) {
        return Policy.builder()
            .policyId(policyId)
            .resource(resource)
            .action(action)
            .effect(PolicyEffect.ALLOW)
            .conditions(conditions != null ? conditions : new HashMap<>())
            .enabled(true)
            .build();
    }

    public static Policy createDenyPolicy(String policyId, String resource, String action, 
                                         Map<String, Object> conditions) {
        return Policy.builder()
            .policyId(policyId)
            .resource(resource)
            .action(action)
            .effect(PolicyEffect.DENY)
            .conditions(conditions != null ? conditions : new HashMap<>())
            .enabled(true)
            .priority(200) // DENY policies get higher priority
            .build();
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public boolean isAllow() {
        return this.effect == PolicyEffect.ALLOW;
    }

    public boolean isDeny() {
        return this.effect == PolicyEffect.DENY;
    }

    @Override
    protected String getModuleCode() {
        return "POL";
    }
}

