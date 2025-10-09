package com.fabricmanagement.company.domain.policy;

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
 * Immutable audit log for policy decisions
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
    private String decision;
    
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
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    public boolean isAllowed() {
        return "ALLOW".equals(decision);
    }
    
    public boolean isDenied() {
        return "DENY".equals(decision);
    }
}

