package com.fabricmanagement.shared.domain.policy;

import com.fabricmanagement.shared.domain.event.DomainEvent;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Policy Audit Event
 * 
 * Published to Kafka when a policy decision is made.
 * Used for event-driven audit processing, analytics, and monitoring.
 * 
 * Event Flow:
 * 1. PolicyEngine evaluates request → PolicyDecision
 * 2. PolicyAuditService logs to DB (sync)
 * 3. PolicyAuditService publishes event to Kafka (async)
 * 4. External systems consume for analytics/alerting
 * 
 * Kafka Topic: policy.audit
 * 
 * @author Fabric Management Team
 * @since 2.0 (Policy Authorization - Phase 3)
 */
@Getter
@ToString(callSuper = true)
@Builder
public class PolicyAuditEvent extends DomainEvent {
    
    // WHO
    @JsonProperty("userId")
    private final UUID userId;
    
    @JsonProperty("userRoles")
    private final String userRoles;  // Comma-separated for analytics
    
    // WHERE (Company Context)
    @JsonProperty("companyId")
    private final UUID companyId;
    
    @JsonProperty("companyType")
    private final String companyType;
    
    // WHAT
    @JsonProperty("endpoint")
    private final String endpoint;
    
    @JsonProperty("httpMethod")
    private final String httpMethod;
    
    @JsonProperty("operation")
    private final String operation;
    
    @JsonProperty("scope")
    private final String scope;
    
    // DECISION
    @JsonProperty("decision")
    private final String decision;  // ALLOW or DENY
    
    @JsonProperty("reason")
    private final String reason;
    
    @JsonProperty("policyVersion")
    private final String policyVersion;
    
    // WHEN & PERFORMANCE
    @JsonProperty("timestamp")
    private final LocalDateTime timestamp;
    
    @JsonProperty("latencyMs")
    private final Integer latencyMs;
    
    // TRACING
    @JsonProperty("requestId")
    private final String requestId;
    
    @JsonProperty("correlationId")
    private final String correlationId;
    
    @JsonProperty("requestIp")
    private final String requestIp;
    
    @Override
    public String getEventType() {
        return "PolicyAuditEvent";
    }
    
    @Override
    public String getAggregateId() {
        // Use correlationId or requestId as aggregate identifier
        return correlationId != null ? correlationId : 
               (requestId != null ? requestId : UUID.randomUUID().toString());
    }
    
    @Override
    public String getTenantId() {
        // Company ID serves as tenant context for audit events
        return companyId != null ? companyId.toString() : null;
    }
    
    /**
     * Factory method to create event from PolicyDecisionAudit
     * 
     * @param audit policy decision audit record
     * @return policy audit event
     */
    public static PolicyAuditEvent fromAudit(PolicyDecisionAudit audit) {
        return PolicyAuditEvent.builder()
            .userId(audit.getUserId())
            .companyId(audit.getCompanyId())
            .companyType(audit.getCompanyType())
            .endpoint(audit.getEndpoint())
            .httpMethod(audit.getHttpMethod())
            .operation(audit.getOperation())
            .scope(audit.getScope())
            .decision(audit.getDecision())
            .reason(audit.getReason())
            .policyVersion(audit.getPolicyVersion())
            .timestamp(audit.getCreatedAt())
            .latencyMs(audit.getLatencyMs())
            .requestId(audit.getRequestId())
            .correlationId(audit.getCorrelationId())
            .requestIp(audit.getRequestIp())
            .userRoles(null) // Can be enriched if needed
            .build();
    }
}

