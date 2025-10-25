package com.fabricmanagement.common.platform.audit.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.audit.domain.AuditLog;
import com.fabricmanagement.common.platform.audit.domain.AuditSeverity;
import com.fabricmanagement.common.platform.audit.infra.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Audit Service - Comprehensive audit logging.
 *
 * <p><b>CRITICAL for compliance:</b> GDPR, ISO 27001, SOC 2</p>
 *
 * <h2>What to Audit:</h2>
 * <ul>
 *   <li>All CREATE, UPDATE, DELETE operations</li>
 *   <li>Authentication events</li>
 *   <li>Policy decisions</li>
 *   <li>Security events</li>
 *   <li>Configuration changes</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * @Service
 * public class MaterialService {
 *     @Autowired
 *     private AuditService auditService;
 *
 *     public void createMaterial(CreateMaterialRequest request) {
 *         Material material = materialRepository.save(...);
 *
 *         auditService.logAction("MATERIAL_CREATE", "material", 
 *             material.getId().toString(), "Material created: " + material.getName());
 *     }
 * }
 * }</pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Log an action (async for performance).
     *
     * @param action the action performed
     * @param resource the resource type
     * @param resourceId the resource ID
     * @param description description of what happened
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(String action, String resource, String resourceId, String description) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        UUID userId = TenantContext.getCurrentUserId();

        AuditLog auditLog = AuditLog.create(
            userId,
            null, // userUid - TODO: get from User service
            action,
            resource,
            resourceId,
            description
        );
        auditLog.setTenantId(tenantId);

        auditLogRepository.save(auditLog);

        log.debug("Audit logged: action={}, resource={}, resourceId={}", action, resource, resourceId);
    }

    /**
     * Log a security event.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSecurityEvent(String action, String description, String ipAddress, AuditSeverity severity) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        UUID userId = TenantContext.getCurrentUserId();

        AuditLog auditLog = AuditLog.createSecurityEvent(userId, action, description, ipAddress, severity);
        auditLog.setTenantId(tenantId);

        auditLogRepository.save(auditLog);

        log.warn("Security event logged: action={}, severity={}, ip={}", action, severity, ipAddress);
    }

    /**
     * Get audit logs for tenant.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogs(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return auditLogRepository.findByTenantIdOrderByTimestampDesc(tenantId, pageable);
    }

    /**
     * Get audit logs by user.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByUser(UUID userId, Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return auditLogRepository.findByTenantIdAndUserIdOrderByTimestampDesc(tenantId, userId, pageable);
    }

    /**
     * Get audit logs by resource.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByResource(String resource, Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return auditLogRepository.findByTenantIdAndResourceOrderByTimestampDesc(tenantId, resource, pageable);
    }

    /**
     * Get audit logs by time range.
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getAuditLogsByTimeRange(Instant start, Instant end) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return auditLogRepository.findByTenantIdAndTimestampBetween(tenantId, start, end);
    }

    /**
     * Get critical/error audit logs for security monitoring.
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getCriticalLogs() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return auditLogRepository.findByTenantIdAndSeverity(tenantId, AuditSeverity.CRITICAL);
    }
}

