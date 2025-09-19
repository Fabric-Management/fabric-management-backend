package com.fabricmanagement.identity.domain.repository;

import com.fabricmanagement.identity.domain.model.UserContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UserContact entities.
 */
@Repository
public interface UserContactRepository extends JpaRepository<UserContact, Long> {
    
    /**
     * Finds user contact by ID.
     */
    Optional<UserContact> findById(Long id);
    
    /**
     * Finds user contacts by user ID.
     */
    List<UserContact> findByUserId(UUID userId);
    
    /**
     * Finds user contacts by tenant ID.
     */
    List<UserContact> findByTenantId(UUID tenantId);
    
    /**
     * Finds user contacts by user ID and tenant ID.
     */
    List<UserContact> findByUserIdAndTenantId(UUID userId, UUID tenantId);
    
    /**
     * Finds active user contacts by user ID.
     */
    @Query("SELECT uc FROM UserContact uc WHERE uc.userId = :userId AND uc.isActive = true AND uc.isDeleted = false")
    List<UserContact> findActiveByUserId(@Param("userId") UUID userId);
    
    /**
     * Finds active user contacts by tenant ID.
     */
    @Query("SELECT uc FROM UserContact uc WHERE uc.tenantId = :tenantId AND uc.isActive = true AND uc.isDeleted = false")
    List<UserContact> findActiveByTenantId(@Param("tenantId") UUID tenantId);
    
    /**
     * Checks if user contact exists by user ID.
     */
    boolean existsByUserId(UUID userId);
    
    /**
     * Checks if user contact exists by user ID and tenant ID.
     */
    boolean existsByUserIdAndTenantId(UUID userId, UUID tenantId);
    
    /**
     * Finds user contacts by status.
     */
    List<UserContact> findByStatus(String status);
    
    /**
     * Finds user contacts by job title.
     */
    List<UserContact> findByJobTitle(String jobTitle);
    
    /**
     * Finds user contacts by department.
     */
    List<UserContact> findByDepartment(String department);
    
    /**
     * Searches user contacts by keyword.
     */
    @Query("SELECT uc FROM UserContact uc WHERE " +
           "(uc.jobTitle LIKE %:keyword% OR " +
           "uc.department LIKE %:keyword% OR " +
           "uc.primaryEmail LIKE %:keyword% OR " +
           "uc.primaryPhone LIKE %:keyword%) AND " +
           "uc.isActive = true AND uc.isDeleted = false")
    List<UserContact> searchByKeyword(@Param("keyword") String keyword);
}
