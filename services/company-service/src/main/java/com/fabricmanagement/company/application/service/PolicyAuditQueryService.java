package com.fabricmanagement.company.application.service;

import com.fabricmanagement.company.application.dto.PolicyAuditResponse;
import com.fabricmanagement.company.application.dto.PolicyAuditStatsResponse;
import com.fabricmanagement.company.application.mapper.PolicyAuditMapper;
import com.fabricmanagement.shared.domain.policy.PolicyDecisionAudit;
import com.fabricmanagement.shared.infrastructure.policy.repository.PolicyDecisionAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Policy Audit Query Service
 * 
 * Read-only service for querying policy decision audit logs.
 * 
 * Used by administrators for:
 * - Security analysis (reviewing denials)
 * - Compliance auditing
 * - Performance monitoring (latency)
 * - Debugging authorization issues
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyAuditQueryService {
    
    private final PolicyDecisionAuditRepository auditRepository;
    private final PolicyAuditMapper auditMapper;
    
    @Transactional(readOnly = true)
    public List<PolicyAuditResponse> getUserAuditLogs(UUID userId, int limit) {
        log.debug("Getting audit logs for user: {}, limit: {}", userId, limit);
        List<PolicyDecisionAudit> logs = auditRepository.findRecentByUser(userId, limit);
        return auditMapper.toResponseList(logs);
    }
    
    @Transactional(readOnly = true)
    public List<PolicyAuditResponse> getDenyDecisions(LocalDateTime since, int limit) {
        log.debug("Getting deny decisions since: {}, limit: {}", since, limit);
        PageRequest pageRequest = PageRequest.of(0, limit);
        Page<PolicyDecisionAudit> page = auditRepository.findDenyDecisions(since, pageRequest);
        return auditMapper.toResponseList(page.getContent());
    }
    
    @Transactional(readOnly = true)
    public PolicyAuditStatsResponse getStatistics(LocalDateTime since) {
        log.debug("Getting audit statistics since: {}", since);
        
        Long allowCount = auditRepository.countByDecisionSince("ALLOW", since);
        Long denyCount = auditRepository.countByDecisionSince("DENY", since);
        Long totalCount = allowCount + denyCount;
        
        Double denyRate = totalCount > 0 
            ? (denyCount.doubleValue() / totalCount.doubleValue()) * 100.0 
            : 0.0;
        
        Double avgLatency = auditRepository.getAverageLatency(since);
        
        return PolicyAuditStatsResponse.builder()
            .totalDecisions(totalCount)
            .allowDecisions(allowCount)
            .denyDecisions(denyCount)
            .denyRate(Math.round(denyRate * 100.0) / 100.0)
            .averageLatencyMs(avgLatency != null ? Math.round(avgLatency * 100.0) / 100.0 : 0.0)
            .build();
    }
    
    @Transactional(readOnly = true)
    public List<PolicyAuditResponse> getByCorrelationId(String correlationId) {
        log.debug("Getting audit logs by correlation ID: {}", correlationId);
        List<PolicyDecisionAudit> logs = auditRepository.findByCorrelationIdOrderByCreatedAt(correlationId);
        return auditMapper.toResponseList(logs);
    }
}

