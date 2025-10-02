package com.fabricmanagement.company.infrastructure.repository;

import com.fabricmanagement.company.domain.valueobject.CompanyUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Company User Repository
 * 
 * Provides data access for company-user relationships
 */
@Repository
public interface CompanyUserRepository extends JpaRepository<CompanyUser, UUID> {
    
    /**
     * Finds all users for a company
     */
    List<CompanyUser> findByCompanyIdAndIsActiveTrue(UUID companyId);
    
    /**
     * Finds all companies for a user
     */
    List<CompanyUser> findByUserIdAndIsActiveTrue(UUID userId);
    
    /**
     * Finds a specific company-user relationship
     */
    Optional<CompanyUser> findByCompanyIdAndUserId(UUID companyId, UUID userId);
    
    /**
     * Checks if a user is in a company
     */
    boolean existsByCompanyIdAndUserIdAndIsActiveTrue(UUID companyId, UUID userId);
    
    /**
     * Counts active users in a company
     */
    long countByCompanyIdAndIsActiveTrue(UUID companyId);
    
    /**
     * Finds users by role in a company
     */
    List<CompanyUser> findByCompanyIdAndRoleAndIsActiveTrue(UUID companyId, String role);
    
    /**
     * Deletes all users from a company (for cleanup)
     */
    void deleteByCompanyId(UUID companyId);
    
    /**
     * Deactivates all users in a company
     */
    @Query("UPDATE CompanyUser cu SET cu.isActive = false WHERE cu.companyId = :companyId")
    int deactivateAllByCompanyId(@Param("companyId") UUID companyId);
}

