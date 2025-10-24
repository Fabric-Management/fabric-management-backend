package com.fabricmanagement.company.api;

import com.fabricmanagement.company.api.dto.response.PolicyAuditResponse;
import com.fabricmanagement.company.api.dto.response.PolicyAuditStatsResponse;
import com.fabricmanagement.company.application.service.PolicyAuditQueryService;
import com.fabricmanagement.shared.application.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Policy Audit Controller
 * 
 * Provides read-only access to policy decision audit logs.
 * Used by administrators for compliance, security analysis, and debugging.
 * 
 * Security: Admin+ only
 * API Version: v1
 * Base Path: /api/v1/policy-audit
 */
@RestController
@RequestMapping("/api/v1/policy-audit")
@RequiredArgsConstructor
@Slf4j
public class PolicyAuditController {
    
    private final PolicyAuditQueryService policyAuditQueryService;
    
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<PolicyAuditResponse>>> getUserAuditLogs(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "50") int limit) {
        
        log.debug("Getting audit logs for user: {}, limit: {}", userId, limit);
        List<PolicyAuditResponse> logs = policyAuditQueryService.getUserAuditLogs(userId, limit);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
    
    @GetMapping("/denials")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<PolicyAuditResponse>>> getDenyDecisions(
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime since,
            @RequestParam(defaultValue = "100") int limit) {
        
        LocalDateTime sinceDate = since != null ? since : LocalDateTime.now().minusDays(7);
        log.debug("Getting deny decisions since: {}, limit: {}", sinceDate, limit);
        List<PolicyAuditResponse> denials = policyAuditQueryService.getDenyDecisions(sinceDate, limit);
        return ResponseEntity.ok(ApiResponse.success(denials));
    }
    
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PolicyAuditStatsResponse>> getStatistics(
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime since) {
        
        LocalDateTime sinceDate = since != null ? since : LocalDateTime.now().minusDays(7);
        log.debug("Getting audit statistics since: {}", sinceDate);
        PolicyAuditStatsResponse stats = policyAuditQueryService.getStatistics(sinceDate);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
    
    @GetMapping("/trace/{correlationId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<PolicyAuditResponse>>> getByCorrelationId(
            @PathVariable String correlationId) {
        
        log.debug("Tracing audit logs by correlation ID: {}", correlationId);
        List<PolicyAuditResponse> logs = policyAuditQueryService.getByCorrelationId(correlationId);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}

