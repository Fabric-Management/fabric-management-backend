package com.fabricmanagement.shared.infrastructure.policy.audit;

import com.fabricmanagement.shared.domain.policy.PolicyContext;
import com.fabricmanagement.shared.domain.policy.PolicyDecision;
import com.fabricmanagement.shared.domain.policy.PolicyDecisionAudit;
import com.fabricmanagement.shared.infrastructure.policy.constants.PolicyConstants;
import com.fabricmanagement.shared.infrastructure.policy.repository.PolicyDecisionAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Policy Audit Service
 * 
 * Logs all policy decisions for audit trail.
 * Provides explainability and compliance tracking.
 * 
 * Audit Requirements:
 * - Log ALL decisions (ALLOW + DENY)
 * - Immutable audit log (no updates/deletes)
 * - Include WHY (reason field)
 * - Include WHO, WHAT, WHEN, WHERE
 * - Include performance metrics (latency)
 * - Include correlation ID (distributed tracing)
 * 
 * Audit Record:
 * - userId: Who made the request?
 * - companyId: Which company?
 * - companyType: What type of company?
 * - endpoint: What was accessed?
 * - operation: What operation?
 * - scope: What scope?
 * - decision: ALLOW or DENY?
 * - reason: WHY? (most important!)
 * - policyVersion: Which policy version?
 * - requestIp: From where?
 * - requestId: Request identifier
 * - correlationId: Distributed trace ID
 * - latencyMs: How long did evaluation take?
 * - timestamp: When?
 * 
 * Performance:
 * - Async logging (non-blocking)
 * - Kafka event-based (fire and forget) - TODO: Phase 3
 * - Max latency: 5ms (should not slow down main request)
 * 
 * Storage:
 * - PostgreSQL (policy_decisions_audit table)
 * - Monthly partitioning for performance - TODO: Phase 4
 * - Archive old data (>6 months) to cold storage - TODO: Phase 4
 * 
 * Design Principles:
 * - Non-blocking (async)
 * - Fail-safe (error doesn't affect main request)
 * - Complete (all fields captured)
 * - Queryable (indexed for analysis)
 * 
 * Usage:
 * <pre>
 * policyAuditService.logDecision(context, decision, latencyMs);
 * </pre>
 * 
 * @author Fabric Management Team
 * @since 2.0 (Policy Authorization)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyAuditService {
    
    private final PolicyDecisionAuditRepository auditRepository;
    // TODO: Inject KafkaTemplate for async event publishing (Phase 3)
    
    /**
     * Log policy decision (async)
     * 
     * @param context policy context
     * @param decision policy decision
     * @param latencyMs evaluation latency in milliseconds
     */
    @Async
    public void logDecision(PolicyContext context, PolicyDecision decision, long latencyMs) {
        try {
            // Log to application log (for debugging)
            if (decision.isAllowed()) {
                log.info("POLICY ALLOW - User: {}, Company: {}, Endpoint: {}, Operation: {}, Reason: {}, Latency: {}ms, Correlation: {}",
                    context.getUserId(),
                    context.getCompanyId(),
                    context.getEndpoint(),
                    context.getOperation(),
                    decision.getReason(),
                    latencyMs,
                    context.getCorrelationId()
                );
            } else {
                log.warn("POLICY DENY - User: {}, Company: {}, Endpoint: {}, Operation: {}, Reason: {}, Latency: {}ms, Correlation: {}",
                    context.getUserId(),
                    context.getCompanyId(),
                    context.getEndpoint(),
                    context.getOperation(),
                    decision.getReason(),
                    latencyMs,
                    context.getCorrelationId()
                );
            }
            
            // Create audit record
            PolicyDecisionAudit audit = PolicyDecisionAudit.builder()
                .userId(context.getUserId())
                .companyId(context.getCompanyId())
                .companyType(context.getCompanyType() != null ? context.getCompanyType().name() : null)
                .endpoint(context.getEndpoint())
                .httpMethod(context.getHttpMethod())
                .operation(context.getOperation() != null ? context.getOperation().name() : null)
                .scope(context.getScope() != null ? context.getScope().name() : null)
                .decision(decision.isAllowed() ? PolicyConstants.DECISION_ALLOW : PolicyConstants.DECISION_DENY)
                .reason(decision.getReason())
                .policyVersion(decision.getPolicyVersion())
                .requestIp(context.getRequestIp())
                .requestId(context.getRequestId())
                .correlationId(context.getCorrelationId())
                .latencyMs((int) latencyMs)
                .createdAt(LocalDateTime.now())
                .build();
            
            // Save to database (async)
            auditRepository.save(audit);
            
            // TODO: Publish to Kafka for event-driven processing (Phase 3)
            // kafkaTemplate.send("policy.audit", createKafkaEvent(audit));
            
        } catch (Exception e) {
            // Fail-safe: Don't let audit failure affect main request
            log.error("Failed to log policy decision audit. Context: {}, Decision: {}",
                context.getEndpoint(), decision.getReason(), e);
        }
    }
    
    /**
     * Log policy decision (sync - for testing only)
     * 
     * @param context policy context
     * @param decision policy decision
     * @param latencyMs evaluation latency
     */
    public void logDecisionSync(PolicyContext context, PolicyDecision decision, long latencyMs) {
        try {
            PolicyDecisionAudit audit = PolicyDecisionAudit.builder()
                .userId(context.getUserId())
                .companyId(context.getCompanyId())
                .companyType(context.getCompanyType() != null ? context.getCompanyType().name() : null)
                .endpoint(context.getEndpoint())
                .httpMethod(context.getHttpMethod())
                .operation(context.getOperation() != null ? context.getOperation().name() : null)
                .scope(context.getScope() != null ? context.getScope().name() : null)
                .decision(decision.isAllowed() ? PolicyConstants.DECISION_ALLOW : PolicyConstants.DECISION_DENY)
                .reason(decision.getReason())
                .policyVersion(decision.getPolicyVersion())
                .correlationId(context.getCorrelationId())
                .latencyMs((int) latencyMs)
                .createdAt(LocalDateTime.now())
                .build();
            
            auditRepository.save(audit);
            log.debug("Sync audit saved: {}", decision.getAuditMessage());
            
        } catch (Exception e) {
            log.error("Failed to save sync audit", e);
        }
    }
    
    /**
     * Query audit logs for user
     * (For compliance / debugging)
     * 
     * @param userId user ID
     * @param limit max results
     * @return audit log summary
     */
    public List<String> getAuditLogsForUser(UUID userId, int limit) {
        try {
            List<PolicyDecisionAudit> audits = auditRepository.findRecentByUser(userId, limit);
            
            return audits.stream()
                .map(audit -> String.format("[%s] %s - %s on %s (%s)",
                    audit.getCreatedAt(),
                    audit.getDecision(),
                    audit.getOperation(),
                    audit.getEndpoint(),
                    audit.getReason()))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error getting audit logs for user: {}", userId, e);
            return List.of();
        }
    }
    
    /**
     * Query DENY decisions for analysis
     * (For security monitoring)
     * 
     * @param hours look back period in hours
     * @return deny decisions
     */
    public List<PolicyDecisionAudit> getDenyDecisions(int hours) {
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(hours);
            Page<PolicyDecisionAudit> denies = auditRepository.findDenyDecisions(
                since,
                PageRequest.of(0, 100)
            );
            
            return denies.getContent();
            
        } catch (Exception e) {
            log.error("Error getting deny decisions", e);
            return List.of();
        }
    }
    
    /**
     * Get audit statistics
     * 
     * @param hours look back period in hours
     * @return stats summary
     */
    public String getStats(int hours) {
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(hours);
            
            Long allowCount = auditRepository.countByDecisionSince(PolicyConstants.DECISION_ALLOW, since);
            Long denyCount = auditRepository.countByDecisionSince(PolicyConstants.DECISION_DENY, since);
            Double avgLatency = auditRepository.getAverageLatency(since);
            
            return String.format("Stats (last %dh): ALLOW=%d, DENY=%d, Avg Latency=%.2fms",
                hours, allowCount, denyCount, avgLatency != null ? avgLatency : 0.0);
                
        } catch (Exception e) {
            log.error("Error getting audit stats", e);
            return "Error getting stats";
        }
    }
}

