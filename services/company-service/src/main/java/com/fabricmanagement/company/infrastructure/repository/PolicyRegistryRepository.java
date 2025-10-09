package com.fabricmanagement.company.infrastructure.repository;

import com.fabricmanagement.company.domain.policy.PolicyRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Policy Registry Repository
 * 
 * Manages endpoint security catalog
 */
@Repository
public interface PolicyRegistryRepository extends JpaRepository<PolicyRegistry, UUID> {
    
    @Query("SELECT p FROM PolicyRegistry p WHERE p.endpoint = :endpoint AND p.active = true AND p.deleted = false")
    Optional<PolicyRegistry> findActiveByEndpoint(@Param("endpoint") String endpoint);
    
    @Query("SELECT p FROM PolicyRegistry p WHERE p.active = true AND p.deleted = false")
    List<PolicyRegistry> findAllActive();
    
    @Query("SELECT p FROM PolicyRegistry p WHERE p.policyVersion = :policyVersion")
    List<PolicyRegistry> findByPolicyVersion(@Param("policyVersion") String policyVersion);
}

