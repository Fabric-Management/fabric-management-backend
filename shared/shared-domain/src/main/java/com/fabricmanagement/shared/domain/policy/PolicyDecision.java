package com.fabricmanagement.shared.domain.policy;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Policy Decision (Immutable)
 * 
 * Represents the result of a policy evaluation by the PDP (Policy Decision Point).
 * 
 * Key Design Decisions:
 * - @Value makes it immutable (no setters)
 * - Final fields ensure thread safety
 * - Builder pattern for flexible construction
 * - Reason field for explainability (audit trail)
 * - CorrelationId for distributed tracing
 * 
 * Immutability Benefits:
 * - Audit trail integrity (cannot be changed after creation)
 * - Thread-safe by design
 * - Prevents tampering with decisions
 * 
 * Usage:
 * <pre>
 * PolicyDecision decision = PolicyDecision.allow(
 *     "role_default_admin",
 *     "v1.2",
 *     "550e8400-e29b-41d4-a716-446655440000"
 * );
 * 
 * PolicyDecision denial = PolicyDecision.deny(
 *     "company_type_guardrail",
 *     "v1.2",
 *     "550e8400-e29b-41d4-a716-446655440000"
 * );
 * </pre>
 * 
 * @author Fabric Management Team
 * @since 2.0 (Policy Authorization)
 */
@Value
@Builder(toBuilder = true)
public class PolicyDecision {
    
    /**
     * Is the request allowed?
     * - true: ALLOW (proceed with request)
     * - false: DENY (reject request)
     */
    boolean allowed;
    
    /**
     * Human-readable reason for the decision
     * 
     * Examples:
     * - "role_default_admin" (allowed because user is admin)
     * - "company_type_guardrail" (denied because customer cannot write)
     * - "scope_invalid" (denied because user cannot access other company data)
     * - "user_grant_explicit_deny" (denied by explicit user permission)
     * 
     * Used for:
     * - Audit trail
     * - Debugging
     * - Compliance reporting
     * - User feedback
     */
    String reason;
    
    /**
     * Policy version that made this decision
     * 
     * Format: "v1.2"
     * 
     * Used for:
     * - Audit trail
     * - Policy change tracking
     * - Rollback capability
     */
    String policyVersion;
    
    /**
     * When was this decision made?
     * 
     * Immutable timestamp of decision evaluation.
     * Used for audit trail and cache expiry.
     */
    LocalDateTime decidedAt;
    
    /**
     * Correlation ID for distributed tracing
     * 
     * Flows through the entire request:
     * Gateway → PDP → Service → Audit
     * 
     * Used for:
     * - Distributed tracing (linking logs across services)
     * - Debugging request flows
     * - Performance analysis
     * 
     * Format: UUID string
     */
    String correlationId;
    
    // =========================================================================
    // FACTORY METHODS
    // =========================================================================
    
    /**
     * Create an ALLOW decision
     * 
     * @param reason why was it allowed?
     * @param policyVersion version of policy used
     * @param correlationId request correlation ID
     * @return immutable ALLOW decision
     */
    public static PolicyDecision allow(String reason, String policyVersion, String correlationId) {
        return PolicyDecision.builder()
            .allowed(true)
            .reason(reason)
            .policyVersion(policyVersion)
            .decidedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
    }
    
    /**
     * Create a DENY decision
     * 
     * @param reason why was it denied?
     * @param policyVersion version of policy used
     * @param correlationId request correlation ID
     * @return immutable DENY decision
     */
    public static PolicyDecision deny(String reason, String policyVersion, String correlationId) {
        return PolicyDecision.builder()
            .allowed(false)
            .reason(reason)
            .policyVersion(policyVersion)
            .decidedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
    }
    
    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    /**
     * Is this a DENY decision?
     * 
     * @return true if denied
     */
    public boolean isDenied() {
        return !allowed;
    }
    
    /**
     * Check if decision is expired (for caching)
     * 
     * @param ttlMinutes TTL in minutes
     * @return true if expired
     */
    public boolean isExpired(int ttlMinutes) {
        return decidedAt.plusMinutes(ttlMinutes).isBefore(LocalDateTime.now());
    }
    
    /**
     * Get audit message for logging
     * 
     * @return formatted audit message
     */
    public String getAuditMessage() {
        return String.format("[%s] %s - %s (policy: %s, correlation: %s)",
            decidedAt,
            allowed ? "ALLOW" : "DENY",
            reason,
            policyVersion,
            correlationId
        );
    }
}

