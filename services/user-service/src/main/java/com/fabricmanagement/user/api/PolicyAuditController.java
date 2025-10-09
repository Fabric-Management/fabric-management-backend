package com.fabricmanagement.user.api;

import com.fabricmanagement.shared.application.annotation.CurrentSecurityContext;
import com.fabricmanagement.shared.application.context.SecurityContext;
import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.user.api.dto.PolicyAuditResponse;
import com.fabricmanagement.user.api.dto.PolicyAuditStatsResponse;
import com.fabricmanagement.user.application.service.PolicyAuditQueryService;
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
    
    /**
     * Get recent audit logs for a user
     * 
     * GET /api/v1/policy-audit/user/{userId}?limit=50
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<PolicyAuditResponse>>> getUserAuditLogs(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "50") int limit,
            @CurrentSecurityContext SecurityContext ctx) {
        
        log.debug("Getting audit logs for user: {}, limit: {}", userId, limit);
        
        List<PolicyAuditResponse> logs = policyAuditQueryService.getUserAuditLogs(userId, limit);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
    
    /**
     * Get DENY decisions (security review)
     * 
     * GET /api/v1/policy-audit/denials?since=2025-01-01T00:00:00&limit=100
     */
    @GetMapping("/denials")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<PolicyAuditResponse>>> getDenyDecisions(
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime since,
            @RequestParam(defaultValue = "100") int limit,
            @CurrentSecurityContext SecurityContext ctx) {
        
        LocalDateTime sinceDate = since != null ? since : LocalDateTime.now().minusDays(7);
        
        log.debug("Getting deny decisions since: {}, limit: {}", sinceDate, limit);
        
        List<PolicyAuditResponse> denials = policyAuditQueryService.getDenyDecisions(sinceDate, limit);
        return ResponseEntity.ok(ApiResponse.success(denials));
    }
    
    /**
     * Get audit statistics
     * 
     * GET /api/v1/policy-audit/stats?since=2025-01-01T00:00:00
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PolicyAuditStatsResponse>> getStatistics(
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime since,
            @CurrentSecurityContext SecurityContext ctx) {
        
        LocalDateTime sinceDate = since != null ? since : LocalDateTime.now().minusDays(7);
        
        log.debug("Getting audit statistics since: {}", sinceDate);
        
        PolicyAuditStatsResponse stats = policyAuditQueryService.getStatistics(sinceDate);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
    
    /**
     * Get audit logs by correlation ID (trace full request)
     * 
     * GET /api/v1/policy-audit/trace/{correlationId}
     */
    @GetMapping("/trace/{correlationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<PolicyAuditResponse>>> getByCorrelationId(
            @PathVariable String correlationId,
            @CurrentSecurityContext SecurityContext ctx) {
        
        log.debug("Tracing audit logs by correlation ID: {}", correlationId);
        
        List<PolicyAuditResponse> logs = policyAuditQueryService.getByCorrelationId(correlationId);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}

