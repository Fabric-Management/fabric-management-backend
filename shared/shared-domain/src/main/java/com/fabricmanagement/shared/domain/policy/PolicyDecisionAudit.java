package com.fabricmanagement.shared.domain.policy;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Policy Decision Audit Entity
 * 
 * Immutable audit log for all policy authorization decisions.
 * 
 * Purpose:
 * - Compliance (WHO accessed WHAT, WHEN, WHY)
 * - Security investigation
 * - Performance monitoring
 * - Audit trail
 * 
 * Business Rules:
 * - Immutable - no updates after creation
 * - Must include reason for explainability
 * - Both ALLOW and DENY decisions are logged
 * 
 * Performance Note:
 * - This table grows fast - consider partitioning
 * - Use async logging (Kafka) to avoid blocking
 */
@Entity
@Table(name = "policy_decisions_audit")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyDecisionAudit {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "company_id")
    private UUID companyId;
    
    @Column(name = "company_type", length = 50)
    private String companyType;
    
    @Column(name = "endpoint", nullable = false, length = 200)
    private String endpoint;
    
    @Column(name = "operation", nullable = false, length = 50)
    private String operation;
    
    @Column(name = "scope", length = 50)
    private String scope;
    
    @Column(name = "decision", nullable = false, length = 10)
    private String decision;  // ALLOW or DENY
    
    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;
    
    @Column(name = "policy_version", length = 20)
    private String policyVersion;
    
    @Column(name = "request_ip", length = 50)
    private String requestIp;
    
    @Column(name = "request_id", length = 100)
    private String requestId;
    
    @Column(name = "correlation_id", length = 100)
    private String correlationId;
    
    @Column(name = "latency_ms")
    private Integer latencyMs;
    
    @Column(name = "created_at", nullable = false)
    @lombok.Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    /**
     * Creates a new audit entry for ALLOW decision
     */
    public static PolicyDecisionAudit allow(UUID userId, UUID companyId, String endpoint,
                                           String operation, String reason) {
        return PolicyDecisionAudit.builder()
            .userId(userId)
            .companyId(companyId)
            .endpoint(endpoint)
            .operation(operation)
            .decision("ALLOW")
            .reason(reason)
            .createdAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Creates a new audit entry for DENY decision
     */
    public static PolicyDecisionAudit deny(UUID userId, UUID companyId, String endpoint,
                                          String operation, String reason) {
        return PolicyDecisionAudit.builder()
            .userId(userId)
            .companyId(companyId)
            .endpoint(endpoint)
            .operation(operation)
            .decision("DENY")
            .reason(reason)
            .createdAt(LocalDateTime.now())
            .build();
    }
}

