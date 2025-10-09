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
    @Query("SELECT p FROM PolicyRegistry p WHERE p.endpoint = :endpoint AND p.active = true AND p.deleted = false")
    Optional<PolicyRegistry> findByEndpointAndActiveTrue(@Param("endpoint") String endpoint);
    
    /**
     * Find active policy by endpoint and operation
     * 
     * @param endpoint API endpoint
     * @param operation operation type
     * @return optional policy
     */
    @Query("SELECT p FROM PolicyRegistry p WHERE p.endpoint = :endpoint AND p.operation = :operation AND p.active = true AND p.deleted = false")
    Optional<PolicyRegistry> findByEndpointAndOperationAndActiveTrue(
        @Param("endpoint") String endpoint, 
        @Param("operation") OperationType operation
    );
    
    /**
     * Find all active policies
     * 
     * @return list of active policies
     */
    @Query("SELECT p FROM PolicyRegistry p WHERE p.active = true AND p.deleted = false")
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
        AND p.deleted = false
        """)
    List<PolicyRegistry> findPoliciesRequiringGrant();
    
    /**
     * Find policies by company type
     * 
     * @param companyType company type
     * @return list of policies
     */
    @Query(value = """
        SELECT * FROM policy_registry 
        WHERE :companyType = ANY(allowed_company_types) 
        AND active = true
        AND deleted = false
        """, nativeQuery = true)
    List<PolicyRegistry> findByCompanyType(@Param("companyType") String companyType);
    
    /**
     * Find policies by role
     * 
     * @param role user role
     * @return list of policies
     */
    @Query(value = """
        SELECT * FROM policy_registry 
        WHERE :role = ANY(default_roles) 
        AND active = true
        AND deleted = false
        """, nativeQuery = true)
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
        AND p.deleted = false
        """)
    Boolean requiresGrant(@Param("endpoint") String endpoint);
    
    /**
     * Get allowed company types for endpoint
     * 
     * @param endpoint API endpoint
     * @return list of allowed company types
     */
    @Query(value = """
        SELECT allowed_company_types FROM policy_registry 
        WHERE endpoint = :endpoint 
        AND active = true
        AND deleted = false
        LIMIT 1
        """, nativeQuery = true)
    String[] getAllowedCompanyTypes(@Param("endpoint") String endpoint);
    
    /**
     * Get default roles for endpoint
     * 
     * @param endpoint API endpoint
     * @return list of default roles
     */
    @Query(value = """
        SELECT default_roles FROM policy_registry 
        WHERE endpoint = :endpoint 
        AND active = true
        AND deleted = false
        LIMIT 1
        """, nativeQuery = true)
    String[] getDefaultRoles(@Param("endpoint") String endpoint);
}

