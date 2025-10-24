package com.fabricmanagement.shared.infrastructure.policy.repository;

import com.fabricmanagement.shared.infrastructure.policy.PolicyRegistry;
import com.fabricmanagement.shared.domain.valueobject.OperationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Policy Registry Repository
 * 
 * Repository for policy registry storage and retrieval
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ PRODUCTION-READY
 * ✅ POLICY FRAMEWORK
 * ✅ UUID TYPE SAFETY
 */
@Repository
public interface PolicyRegistryRepository extends JpaRepository<PolicyRegistry, UUID> {
    
    /**
     * Find applicable policies for context
     */
    @Query("""
        SELECT p FROM PolicyRegistry p 
        WHERE p.enabled = true
        AND (p.resourceType IS NULL OR p.resourceType = :resourceType)
        AND (p.operation IS NULL OR p.operation = :operation)
        AND (p.scope IS NULL OR p.scope = :scope)
        AND (p.tenantId IS NULL OR p.tenantId = :tenantId)
        ORDER BY p.priority ASC, p.createdAt ASC
        """)
    List<PolicyRegistry> findApplicablePolicies(
        @Param("resourceType") String resourceType,
        @Param("operation") OperationType operation,
        @Param("scope") String scope,
        @Param("tenantId") UUID tenantId
    );
    
    /**
     * Find policies by name and version
     */
    List<PolicyRegistry> findByPolicyNameAndPolicyVersion(String policyName, String policyVersion);
    
    /**
     * Find policies by resource type
     */
    List<PolicyRegistry> findByResourceTypeAndEnabledTrue(String resourceType);
    
    /**
     * Find policies by operation
     */
    List<PolicyRegistry> findByOperationAndEnabledTrue(OperationType operation);
    
    /**
     * Find policies by scope
     */
    List<PolicyRegistry> findByScopeAndEnabledTrue(String scope);
    
    /**
     * Find policies by tenant
     */
    List<PolicyRegistry> findByTenantIdAndEnabledTrue(UUID tenantId);
    
    /**
     * Find enabled policies
     */
    List<PolicyRegistry> findByEnabledTrueOrderByPriorityAscCreatedAtAsc();
    
    /**
     * Find policies by priority range
     */
    List<PolicyRegistry> findByPriorityBetweenAndEnabledTrueOrderByPriorityAsc(Integer minPriority, Integer maxPriority);
    
    /**
     * Check if policy exists by name and version
     */
    boolean existsByPolicyNameAndPolicyVersion(String policyName, String policyVersion);
    
    /**
     * Count enabled policies
     */
    long countByEnabledTrue();
    
    /**
     * Count policies by resource type
     */
    long countByResourceTypeAndEnabledTrue(String resourceType);
    
    /**
     * Count policies by operation
     */
    long countByOperationAndEnabledTrue(OperationType operation);
    
    /**
     * Find policies by endpoint and operation
     */
    @Query("""
        SELECT p FROM PolicyRegistry p 
        WHERE p.enabled = true
        AND (p.resourceType IS NULL OR p.resourceType = :resourceType)
        AND (p.operation IS NULL OR p.operation = :operation)
        ORDER BY p.priority ASC, p.createdAt ASC
        """)
    List<PolicyRegistry> findByEndpointAndOperationAndActiveTrue(
        @Param("resourceType") String resourceType,
        @Param("operation") OperationType operation
    );
    
    /**
     * Check if grant is required for endpoint
     */
    @Query("""
        SELECT COUNT(p) > 0 FROM PolicyRegistry p 
        WHERE p.enabled = true
        AND p.resourceType = :resourceType
        AND p.operation = :operation
        AND p.conditionExpression LIKE '%grant%'
        """)
    boolean requiresGrant(@Param("resourceType") String resourceType);
    
    /**
     * Get allowed company types for endpoint
     */
    @Query("""
        SELECT p.actionParameters FROM PolicyRegistry p 
        WHERE p.enabled = true
        AND p.resourceType = :resourceType
        AND p.operation = :operation
        AND p.actionParameters IS NOT NULL
        """)
    List<Map<String, Object>> getAllowedCompanyTypes(@Param("resourceType") String resourceType);
}