package com.fabricmanagement.common.platform.communication.app;

import com.fabricmanagement.common.platform.communication.domain.EmailOutbox;
import com.fabricmanagement.common.platform.communication.domain.EmailOutboxStatus;
import com.fabricmanagement.common.platform.communication.infra.repository.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Email Diagnostic Service - Systematic analysis of email system health.
 * 
 * <p>Provides comprehensive diagnostics for email delivery issues.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailDiagnosticService {

    private final EmailOutboxRepository emailOutboxRepository;

    @Value("${application.email.use-outbox:true}")
    private boolean useOutbox;

    @Value("${application.email.outbox.worker-enabled:true}")
    private boolean emailOutboxWorkerEnabled;

    /**
     * Comprehensive email system diagnostics.
     * 
     * @return Diagnostic report with all findings
     */
    @Transactional(readOnly = true)
    public Map<String, Object> diagnoseEmailSystem() {
        Map<String, Object> report = new HashMap<>();
        
        log.info("🔍 Starting comprehensive email system diagnostics...");
        
        // 1. Configuration Check
        Map<String, Object> config = checkConfiguration();
        report.put("configuration", config);
        
        // 2. Email Queue Status
        Map<String, Object> queueStatus = checkEmailQueue();
        report.put("queueStatus", queueStatus);
        
        // 3. Recent Emails Analysis
        Map<String, Object> recentEmails = analyzeRecentEmails();
        report.put("recentEmails", recentEmails);
        
        // 4. Failed Emails Analysis
        Map<String, Object> failedEmails = analyzeFailedEmails();
        report.put("failedEmails", failedEmails);
        
        // 5. Recommendations
        List<String> recommendations = generateRecommendations(report);
        report.put("recommendations", recommendations);
        
        log.info("✅ Email system diagnostics completed");
        
        return report;
    }

    private Map<String, Object> checkConfiguration() {
        Map<String, Object> config = new HashMap<>();
        
        config.put("useOutbox", useOutbox);
        config.put("emailOutboxWorkerEnabled", emailOutboxWorkerEnabled);
        config.put("status", useOutbox && emailOutboxWorkerEnabled ? "✅ OK" : "⚠️ WARNING");
        
        if (!useOutbox) {
            config.put("warning", "Email outbox is disabled - emails sent directly (no persistence)");
        }
        if (!emailOutboxWorkerEnabled) {
            config.put("warning", "Email outbox worker is disabled - emails queued but not processed");
        }
        
        return config;
    }

    private Map<String, Object> checkEmailQueue() {
        Map<String, Object> status = new HashMap<>();
        
        long pendingCount = emailOutboxRepository.countByStatusAndIsActiveTrue(EmailOutboxStatus.PENDING);
        long sendingCount = emailOutboxRepository.countByStatusAndIsActiveTrue(EmailOutboxStatus.SENDING);
        long sentCount = emailOutboxRepository.countByStatusAndIsActiveTrue(EmailOutboxStatus.SENT);
        long failedCount = emailOutboxRepository.countByStatusAndIsActiveTrue(EmailOutboxStatus.FAILED);
        
        status.put("pending", pendingCount);
        status.put("sending", sendingCount);
        status.put("sent", sentCount);
        status.put("failed", failedCount);
        status.put("total", pendingCount + sendingCount + sentCount + failedCount);
        
        // Check for stuck emails (SENDING status for > 5 minutes)
        List<EmailOutbox> stuckEmails = emailOutboxRepository.findAll().stream()
            .filter(e -> e.getStatus() == EmailOutboxStatus.SENDING && e.getIsActive())
            .filter(e -> e.getUpdatedAt() != null && 
                e.getUpdatedAt().isBefore(Instant.now().minus(5, ChronoUnit.MINUTES)))
            .toList();
        
        status.put("stuckEmails", stuckEmails.size());
        if (!stuckEmails.isEmpty()) {
            status.put("stuckEmailsDetails", stuckEmails.stream()
                .map(e -> {
                    Map<String, Object> stuckMap = new HashMap<>();
                    stuckMap.put("id", e.getId().toString());
                    stuckMap.put("recipient", e.getRecipient());
                    stuckMap.put("status", e.getStatus().toString());
                    stuckMap.put("updatedAt", e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : "");
                    stuckMap.put("retryCount", e.getRetryCount());
                    return stuckMap;
                })
                .toList());
        }
        
        return status;
    }

    private Map<String, Object> analyzeRecentEmails() {
        Map<String, Object> analysis = new HashMap<>();
        
        // Get emails from last 24 hours
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        List<EmailOutbox> recentEmails = emailOutboxRepository.findAll().stream()
            .filter(e -> e.getCreatedAt() != null && e.getCreatedAt().isAfter(since))
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .limit(20)
            .toList();
        
        analysis.put("count", recentEmails.size());
        analysis.put("emails", recentEmails.stream()
            .map(e -> {
                Map<String, Object> emailMap = new HashMap<>();
                emailMap.put("id", e.getId().toString());
                emailMap.put("recipient", e.getRecipient());
                emailMap.put("status", e.getStatus().toString());
                emailMap.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : "");
                emailMap.put("retryCount", e.getRetryCount());
                emailMap.put("lastError", e.getLastError() != null ? e.getLastError() : "");
                return emailMap;
            })
            .toList());
        
        return analysis;
    }

    private Map<String, Object> analyzeFailedEmails() {
        Map<String, Object> analysis = new HashMap<>();
        
        List<EmailOutbox> failedEmails = emailOutboxRepository
            .findByStatusAndIsActiveTrueOrderByCreatedAtDesc(EmailOutboxStatus.FAILED);
        
        analysis.put("count", failedEmails.size());
        
        if (!failedEmails.isEmpty()) {
            // Group by error message
            Map<String, Long> errorGroups = new HashMap<>();
            failedEmails.forEach(e -> {
                String error = e.getLastError() != null ? e.getLastError() : "Unknown error";
                errorGroups.put(error, errorGroups.getOrDefault(error, 0L) + 1);
            });
            
            analysis.put("errorGroups", errorGroups);
            analysis.put("recentFailures", failedEmails.stream()
                .limit(10)
                .map(e -> {
                    Map<String, Object> failureMap = new HashMap<>();
                    failureMap.put("id", e.getId().toString());
                    failureMap.put("recipient", e.getRecipient());
                    failureMap.put("lastError", e.getLastError() != null ? e.getLastError() : "");
                    failureMap.put("retryCount", e.getRetryCount());
                    failureMap.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : "");
                    return failureMap;
                })
                .toList());
        }
        
        return analysis;
    }

    private List<String> generateRecommendations(Map<String, Object> report) {
        List<String> recommendations = new java.util.ArrayList<>();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) report.get("configuration");
        @SuppressWarnings("unchecked")
        Map<String, Object> queueStatus = (Map<String, Object>) report.get("queueStatus");
        
        // Check configuration
        if (!Boolean.TRUE.equals(config.get("useOutbox"))) {
            recommendations.add("⚠️ Enable email outbox for reliable email delivery: application.email.use-outbox=true");
        }
        if (!Boolean.TRUE.equals(config.get("emailOutboxWorkerEnabled"))) {
            recommendations.add("⚠️ Enable email outbox worker: application.email.outbox.worker-enabled=true");
        }
        
        // Check queue status
        Long pendingCount = ((Number) queueStatus.get("pending")).longValue();
        Long stuckCount = ((Number) queueStatus.get("stuckEmails")).longValue();
        Long failedCount = ((Number) queueStatus.get("failed")).longValue();
        
        if (pendingCount > 10) {
            recommendations.add("⚠️ High pending email count: " + pendingCount + ". Check if background job is running.");
        }
        if (stuckCount > 0) {
            recommendations.add("🚨 CRITICAL: " + stuckCount + " email(s) stuck in SENDING status. Manual intervention required.");
        }
        if (failedCount > 5) {
            recommendations.add("🚨 CRITICAL: " + failedCount + " failed email(s). Check SMTP configuration and network connectivity.");
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("✅ Email system appears healthy");
        }
        
        return recommendations;
    }
}

