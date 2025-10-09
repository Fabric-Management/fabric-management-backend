package com.fabricmanagement.company.infrastructure.repository;

import com.fabricmanagement.company.domain.valueobject.CompanyUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
    @Query("SELECT cu FROM CompanyUser cu WHERE cu.companyId = :companyId AND cu.isActive = true AND cu.deleted = false")
    List<CompanyUser> findByCompanyIdAndIsActiveTrue(@Param("companyId") UUID companyId);
    
    /**
     * Finds all companies for a user
     */
    @Query("SELECT cu FROM CompanyUser cu WHERE cu.userId = :userId AND cu.isActive = true AND cu.deleted = false")
    List<CompanyUser> findByUserIdAndIsActiveTrue(@Param("userId") UUID userId);
    
    /**
     * Finds a specific company-user relationship
     */
    @Query("SELECT cu FROM CompanyUser cu WHERE cu.companyId = :companyId AND cu.userId = :userId AND cu.deleted = false")
    Optional<CompanyUser> findByCompanyIdAndUserId(@Param("companyId") UUID companyId, @Param("userId") UUID userId);
    
    /**
     * Checks if a user is in a company
     */
    @Query("SELECT CASE WHEN COUNT(cu) > 0 THEN true ELSE false END FROM CompanyUser cu WHERE cu.companyId = :companyId AND cu.userId = :userId AND cu.isActive = true AND cu.deleted = false")
    boolean existsByCompanyIdAndUserIdAndIsActiveTrue(@Param("companyId") UUID companyId, @Param("userId") UUID userId);
    
    /**
     * Counts active users in a company
     */
    @Query("SELECT COUNT(cu) FROM CompanyUser cu WHERE cu.companyId = :companyId AND cu.isActive = true AND cu.deleted = false")
    long countByCompanyIdAndIsActiveTrue(@Param("companyId") UUID companyId);
    
    /**
     * Finds users by role in a company
     */
    @Query("SELECT cu FROM CompanyUser cu WHERE cu.companyId = :companyId AND cu.role = :role AND cu.isActive = true AND cu.deleted = false")
    List<CompanyUser> findByCompanyIdAndRoleAndIsActiveTrue(@Param("companyId") UUID companyId, @Param("role") String role);
    
    /**
     * Soft deletes all users from a company (for cleanup)
     */
    @Modifying
    @Query("UPDATE CompanyUser cu SET cu.deleted = true WHERE cu.companyId = :companyId")
    void deleteByCompanyId(@Param("companyId") UUID companyId);
    
    /**
     * Deactivates all users in a company
     */
    @Modifying
    @Query("UPDATE CompanyUser cu SET cu.isActive = false WHERE cu.companyId = :companyId AND cu.deleted = false")
    int deactivateAllByCompanyId(@Param("companyId") UUID companyId);
}

