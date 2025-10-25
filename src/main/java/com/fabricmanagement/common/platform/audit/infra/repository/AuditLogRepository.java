package com.fabricmanagement.common.platform.audit.infra.repository;

import com.fabricmanagement.common.platform.audit.domain.AuditLog;
import com.fabricmanagement.common.platform.audit.domain.AuditSeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for AuditLog entity.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByTenantIdOrderByTimestampDesc(UUID tenantId, Pageable pageable);

    Page<AuditLog> findByTenantIdAndUserIdOrderByTimestampDesc(UUID tenantId, UUID userId, Pageable pageable);

    Page<AuditLog> findByTenantIdAndResourceOrderByTimestampDesc(UUID tenantId, String resource, Pageable pageable);

    Page<AuditLog> findByTenantIdAndActionOrderByTimestampDesc(UUID tenantId, String action, Pageable pageable);

    List<AuditLog> findByTenantIdAndSeverity(UUID tenantId, AuditSeverity severity);

    @Query("SELECT a FROM AuditLog a WHERE a.tenantId = :tenantId AND a.timestamp BETWEEN :start AND :end ORDER BY a.timestamp DESC")
    List<AuditLog> findByTenantIdAndTimestampBetween(
        @Param("tenantId") UUID tenantId,
        @Param("start") Instant start,
        @Param("end") Instant end);

    long countByTenantIdAndSeverity(UUID tenantId, AuditSeverity severity);
}

