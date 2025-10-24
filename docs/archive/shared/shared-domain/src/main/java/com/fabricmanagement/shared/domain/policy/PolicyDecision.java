package com.fabricmanagement.shared.domain.policy;

import com.fabricmanagement.shared.domain.valueobject.OperationType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Policy Decision
 * 
 * Result of policy evaluation
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
public class PolicyDecision {
    
    // =========================================================================
    // DECISION RESULT
    // =========================================================================
    private boolean allowed;
    private String reason;
    private String policyName;
    private String policyVersion;
    
    // =========================================================================
    // CONTEXT INFORMATION
    // =========================================================================
    private UUID userId;
    private UUID tenantId;
    private String resourceType;
    private OperationType operation;
    
    // =========================================================================
    // DECISION METADATA
    // =========================================================================
    private LocalDateTime evaluatedAt;
    private long evaluationTimeMs;
    private String traceId;
    private String correlationId;
    
    // =========================================================================
    // ADDITIONAL DATA
    // =========================================================================
    private Map<String, Object> metadata;
    private Map<String, Object> conditions;
    
    // =========================================================================
    // UTILITY METHODS
    // =========================================================================
    
    /**
     * Check if decision is allowed
     */
    public boolean isAllowed() {
        return allowed;
    }
    
    /**
     * Check if decision is denied
     */
    public boolean isDenied() {
        return !allowed;
    }
    
    /**
     * Get decision reason or default message
     */
    public String getReasonOrDefault(String defaultReason) {
        return reason != null ? reason : defaultReason;
    }
    
    /**
     * Get metadata value
     */
    public Object getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }
    
    /**
     * Get condition value
     */
    public Object getCondition(String key) {
        return conditions != null ? conditions.get(key) : null;
    }
    
    /**
     * Check if decision has specific condition
     */
    public boolean hasCondition(String key) {
        return conditions != null && conditions.containsKey(key);
    }
    
    /**
     * Create allowed decision
     */
    public static PolicyDecision allow(String reason) {
        return PolicyDecision.builder()
            .allowed(true)
            .reason(reason)
            .evaluatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create allowed decision with policy info
     */
    public static PolicyDecision allow(String reason, String policyName, String policyVersion) {
        return PolicyDecision.builder()
            .allowed(true)
            .reason(reason)
            .policyName(policyName)
            .policyVersion(policyVersion)
            .evaluatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create denied decision
     */
    public static PolicyDecision deny(String reason) {
        return PolicyDecision.builder()
            .allowed(false)
            .reason(reason)
            .evaluatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create denied decision with policy info
     */
    public static PolicyDecision deny(String reason, String policyName, String policyVersion) {
        return PolicyDecision.builder()
            .allowed(false)
            .reason(reason)
            .policyName(policyName)
            .policyVersion(policyVersion)
            .evaluatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create decision with context
     */
    public static PolicyDecision create(boolean allowed, String reason, PolicyContext context) {
        return PolicyDecision.builder()
            .allowed(allowed)
            .reason(reason)
            .userId(context.getUserId())
            .tenantId(context.getTenantId())
            .resourceType(context.getResourceType())
            .operation(context.getOperation())
            .traceId(context.getTraceId())
            .correlationId(context.getCorrelationId())
            .evaluatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Check if decision is expired (for caching)
     */
    public boolean isExpired(int ttlMinutes) {
        if (evaluatedAt == null) return true;
        return evaluatedAt.isBefore(LocalDateTime.now().minusMinutes(ttlMinutes));
    }
    
    /**
     * Get audit message for logging
     */
    public String getAuditMessage() {
        return String.format("Policy decision: %s - %s (Policy: %s v%s)", 
            allowed ? "ALLOW" : "DENY", 
            reason != null ? reason : "No reason provided",
            policyName != null ? policyName : "Unknown",
            policyVersion != null ? policyVersion : "Unknown");
    }
}