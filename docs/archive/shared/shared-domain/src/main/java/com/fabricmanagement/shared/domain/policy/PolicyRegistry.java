package com.fabricmanagement.shared.domain.policy;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Policy Registry
 * 
 * Registry for policy definitions and their implementations
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ PRODUCTION-READY
 * ✅ POLICY FRAMEWORK
 * ✅ UUID TYPE SAFETY
 */
@Getter
@Setter
@Builder
@ToString
public class PolicyRegistry {
    
    // =========================================================================
    // POLICY IDENTIFICATION
    // =========================================================================
    private String policyName;
    private String policyVersion;
    private String policyDescription;
    
    // =========================================================================
    // POLICY SCOPE
    // =========================================================================
    private String resourceType;
    private OperationType operation;
    private String scope;
    
    // =========================================================================
    // POLICY IMPLEMENTATION
    // =========================================================================
    private String policyClass;
    private String policyMethod;
    private Map<String, Object> policyParameters;
    
    // =========================================================================
    // POLICY METADATA
    // =========================================================================
    private boolean enabled;
    private int priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    
    // =========================================================================
    // POLICY CONDITIONS
    // =========================================================================
    private Map<String, Object> conditions;
    private Map<String, Object> exceptions;
    
    // =========================================================================
    // UTILITY METHODS
    // =========================================================================
    
    /**
     * Check if policy is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Check if policy applies to resource type
     */
    public boolean appliesToResourceType(String resourceType) {
        return this.resourceType == null || this.resourceType.equals(resourceType);
    }
    
    /**
     * Check if policy applies to operation
     */
    public boolean appliesToOperation(OperationType operation) {
        return this.operation == null || this.operation.equals(operation);
    }
    
    /**
     * Check if policy applies to scope
     */
    public boolean appliesToScope(String scope) {
        return this.scope == null || this.scope.equals(scope);
    }
    
    /**
     * Get policy parameter
     */
    public Object getPolicyParameter(String key) {
        return policyParameters != null ? policyParameters.get(key) : null;
    }
    
    /**
     * Get condition value
     */
    public Object getCondition(String key) {
        return conditions != null ? conditions.get(key) : null;
    }
    
    /**
     * Get exception value
     */
    public Object getException(String key) {
        return exceptions != null ? exceptions.get(key) : null;
    }
    
    /**
     * Check if policy has specific condition
     */
    public boolean hasCondition(String key) {
        return conditions != null && conditions.containsKey(key);
    }
    
    /**
     * Check if policy has specific exception
     */
    public boolean hasException(String key) {
        return exceptions != null && exceptions.containsKey(key);
    }
    
    /**
     * Check if policy is valid
     */
    public boolean isValid() {
        return policyName != null && 
               policyVersion != null && 
               policyClass != null && 
               policyMethod != null;
    }
    
    /**
     * Create builder with common fields
     */
    public static PolicyRegistryBuilder builder(String policyName, String policyVersion) {
        return PolicyRegistry.builder()
            .policyName(policyName)
            .policyVersion(policyVersion)
            .enabled(true)
            .priority(100)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now());
    }
}