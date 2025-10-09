package com.fabricmanagement.shared.infrastructure.policy.repository;

import com.fabricmanagement.shared.domain.policy.OperationType;
import com.fabricmanagement.shared.domain.policy.PolicyRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PolicyRegistry Repository
 * 
 * Repository for platform-wide policy definitions.
 * 
 * @author Fabric Management Team
 * @since 2.0 (Policy Authorization)
 */
@Repository
public interface PolicyRegistryRepository extends JpaRepository<PolicyRegistry, UUID> {
    
    /**
     * Find active policy by endpoint
     * 
     * @param endpoint API endpoint
     * @return optional policy
     */
    Optional<PolicyRegistry> findByEndpointAndActiveTrue(String endpoint);
    
    /**
     * Find active policy by endpoint and operation
     * 
     * @param endpoint API endpoint
     * @param operation operation type
     * @return optional policy
     */
    Optional<PolicyRegistry> findByEndpointAndOperationAndActiveTrue(
        String endpoint, 
        OperationType operation
    );
    
    /**
     * Find all active policies
     * 
     * @return list of active policies
     */
    List<PolicyRegistry> findByActiveTrue();
    
    /**
     * Find policies that require explicit grant
     * 
     * @return list of policies
     */
    @Query("""
        SELECT p FROM PolicyRegistry p 
        WHERE p.requiresGrant = true 
        AND p.active = true
        """)
    List<PolicyRegistry> findPoliciesRequiringGrant();
    
    /**
     * Find policies by company type
     * 
     * @param companyType company type
     * @return list of policies
     */
    @Query("""
        SELECT p FROM PolicyRegistry p 
        WHERE :companyType = ANY(p.allowedCompanyTypes) 
        AND p.active = true
        """)
    List<PolicyRegistry> findByCompanyType(@Param("companyType") String companyType);
    
    /**
     * Find policies by role
     * 
     * @param role user role
     * @return list of policies
     */
    @Query("""
        SELECT p FROM PolicyRegistry p 
        WHERE :role = ANY(p.defaultRoles) 
        AND p.active = true
        """)
    List<PolicyRegistry> findByRole(@Param("role") String role);
    
    /**
     * Check if endpoint requires grant
     * 
     * @param endpoint API endpoint
     * @return true if requires grant
     */
    @Query("""
        SELECT COALESCE(p.requiresGrant, false) FROM PolicyRegistry p 
        WHERE p.endpoint = :endpoint 
        AND p.active = true
        """)
    Boolean requiresGrant(@Param("endpoint") String endpoint);
    
    /**
     * Get allowed company types for endpoint
     * 
     * @param endpoint API endpoint
     * @return list of allowed company types
     */
    @Query("""
        SELECT p.allowedCompanyTypes FROM PolicyRegistry p 
        WHERE p.endpoint = :endpoint 
        AND p.active = true
        """)
    List<String> getAllowedCompanyTypes(@Param("endpoint") String endpoint);
    
    /**
     * Get default roles for endpoint
     * 
     * @param endpoint API endpoint
     * @return list of default roles
     */
    @Query("""
        SELECT p.defaultRoles FROM PolicyRegistry p 
        WHERE p.endpoint = :endpoint 
        AND p.active = true
        """)
    List<String> getDefaultRoles(@Param("endpoint") String endpoint);
}

