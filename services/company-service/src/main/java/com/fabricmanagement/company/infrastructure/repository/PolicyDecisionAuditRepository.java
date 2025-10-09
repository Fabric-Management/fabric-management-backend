package com.fabricmanagement.company.infrastructure.repository;

import com.fabricmanagement.company.domain.policy.PolicyDecisionAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Policy Decision Audit Repository
 * 
 * Append-only repository for policy audit logs
 */
@Repository
public interface PolicyDecisionAuditRepository extends JpaRepository<PolicyDecisionAudit, UUID> {
    
    @Query("SELECT a FROM PolicyDecisionAudit a WHERE a.userId = :userId ORDER BY a.createdAt DESC")
    List<PolicyDecisionAudit> findByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT a FROM PolicyDecisionAudit a WHERE a.decision = 'DENY' ORDER BY a.createdAt DESC")
    List<PolicyDecisionAudit> findDeniedRequests();
    
    @Query("SELECT a FROM PolicyDecisionAudit a WHERE a.correlationId = :correlationId")
    List<PolicyDecisionAudit> findByCorrelationId(@Param("correlationId") String correlationId);
    
    @Query("SELECT a FROM PolicyDecisionAudit a WHERE a.createdAt >= :startDate ORDER BY a.createdAt DESC")
    List<PolicyDecisionAudit> findSince(@Param("startDate") LocalDateTime startDate);
}

