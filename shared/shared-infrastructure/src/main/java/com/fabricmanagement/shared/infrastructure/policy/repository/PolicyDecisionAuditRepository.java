package com.fabricmanagement.shared.infrastructure.policy.repository;

import com.fabricmanagement.shared.domain.policy.PolicyDecisionAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * PolicyDecisionAudit Repository
 * 
 * Repository for immutable audit log of policy decisions.
 * 
 * @author Fabric Management Team
 * @since 2.0 (Policy Authorization)
 */
@Repository
public interface PolicyDecisionAuditRepository extends JpaRepository<PolicyDecisionAudit, UUID> {
    
    /**
     * Find audit logs for user (paginated)
     * 
     * @param userId user ID
     * @param pageable pagination
     * @return page of audit logs
     */
    Page<PolicyDecisionAudit> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    
    /**
     * Find recent audit logs for user
     * 
     * @param userId user ID
     * @param limit max results
     * @return list of audit logs
     */
    @Query("""
        SELECT a FROM PolicyDecisionAudit a 
        WHERE a.userId = :userId 
        ORDER BY a.createdAt DESC
        LIMIT :limit
        """)
    List<PolicyDecisionAudit> findRecentByUser(@Param("userId") UUID userId, @Param("limit") int limit);
    
    /**
     * Find DENY decisions in time range
     * 
     * @param since start time
     * @param pageable pagination
     * @return page of deny decisions
     */
    @Query("""
        SELECT a FROM PolicyDecisionAudit a 
        WHERE a.decision = 'DENY' 
        AND a.createdAt >= :since
        ORDER BY a.createdAt DESC
        """)
    Page<PolicyDecisionAudit> findDenyDecisions(
        @Param("since") LocalDateTime since,
        Pageable pageable
    );
    
    /**
     * Find deny decisions for user
     * 
     * @param userId user ID
     * @param since start time
     * @return list of deny decisions
     */
    @Query("""
        SELECT a FROM PolicyDecisionAudit a 
        WHERE a.userId = :userId 
        AND a.decision = 'DENY'
        AND a.createdAt >= :since
        ORDER BY a.createdAt DESC
        """)
    List<PolicyDecisionAudit> findUserDenyDecisions(
        @Param("userId") UUID userId,
        @Param("since") LocalDateTime since
    );
    
    /**
     * Count decisions by type in time range
     * 
     * @param decision ALLOW or DENY
     * @param since start time
     * @return count
     */
    @Query("""
        SELECT COUNT(a) FROM PolicyDecisionAudit a 
        WHERE a.decision = :decision 
        AND a.createdAt >= :since
        """)
    Long countByDecisionSince(
        @Param("decision") String decision,
        @Param("since") LocalDateTime since
    );
    
    /**
     * Get average latency in time range
     * 
     * @param since start time
     * @return average latency in ms
     */
    @Query("""
        SELECT AVG(a.latencyMs) FROM PolicyDecisionAudit a 
        WHERE a.latencyMs IS NOT NULL
        AND a.createdAt >= :since
        """)
    Double getAverageLatency(@Param("since") LocalDateTime since);
    
    /**
     * Get top deny reasons
     * 
     * @param since start time
     * @param limit top N
     * @return list of [reason, count] pairs
     */
    @Query("""
        SELECT a.reason, COUNT(a) as cnt 
        FROM PolicyDecisionAudit a 
        WHERE a.decision = 'DENY'
        AND a.createdAt >= :since
        GROUP BY a.reason
        ORDER BY cnt DESC
        LIMIT :limit
        """)
    List<Object[]> getTopDenyReasons(@Param("since") LocalDateTime since, @Param("limit") int limit);
    
    /**
     * Find audits by correlation ID (for distributed tracing)
     * 
     * @param correlationId correlation ID
     * @return list of related audits
     */
    List<PolicyDecisionAudit> findByCorrelationIdOrderByCreatedAt(String correlationId);
    
    /**
     * Find audits for endpoint
     * 
     * @param endpoint API endpoint
     * @param since start time
     * @param pageable pagination
     * @return page of audits
     */
    @Query("""
        SELECT a FROM PolicyDecisionAudit a 
        WHERE a.endpoint = :endpoint 
        AND a.createdAt >= :since
        ORDER BY a.createdAt DESC
        """)
    Page<PolicyDecisionAudit> findByEndpointSince(
        @Param("endpoint") String endpoint,
        @Param("since") LocalDateTime since,
        Pageable pageable
    );
    
    /**
     * Find audits by company
     * 
     * @param companyId company ID
     * @param since start time
     * @param pageable pagination
     * @return page of audits
     */
    @Query("""
        SELECT a FROM PolicyDecisionAudit a 
        WHERE a.companyId = :companyId 
        AND a.createdAt >= :since
        ORDER BY a.createdAt DESC
        """)
    Page<PolicyDecisionAudit> findByCompanySince(
        @Param("companyId") UUID companyId,
        @Param("since") LocalDateTime since,
        Pageable pageable
    );
}

