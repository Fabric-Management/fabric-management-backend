package com.fabricmanagement.user.infrastructure.repository;

import com.fabricmanagement.user.domain.aggregate.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * User Repository Interface
 * 
 * Provides data access methods for User aggregate
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    /**
     * Find user by contact value (email or phone)
     */
    @Query("SELECT u FROM User u JOIN u.contacts c WHERE c.contactValue = :contactValue AND u.deleted = false")
    Optional<User> findByContactValue(@Param("contactValue") String contactValue);
    
    /**
     * Find users by tenant ID
     */
    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND u.deleted = false")
    List<User> findByTenantId(@Param("tenantId") UUID tenantId);
    
    /**
     * Find users by status
     */
    @Query("SELECT u FROM User u WHERE u.status = :status AND u.deleted = false")
    List<User> findByStatus(@Param("status") String status);
    
    /**
     * Find users by registration type
     */
    @Query("SELECT u FROM User u WHERE u.registrationType = :registrationType AND u.deleted = false")
    List<User> findByRegistrationType(@Param("registrationType") String registrationType);
    
    /**
     * Search users by name (first name or last name contains)
     */
    @Query("SELECT u FROM User u WHERE (LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "AND u.deleted = false")
    List<User> searchByName(@Param("searchTerm") String searchTerm);
    
    /**
     * Check if contact value exists
     */
    @Query("SELECT COUNT(c) > 0 FROM UserContact c WHERE c.contactValue = :contactValue")
    boolean existsByContactValue(@Param("contactValue") String contactValue);
    
    /**
     * Find users with verified contacts only
     */
    @Query("SELECT u FROM User u JOIN u.contacts c WHERE c.isVerified = true AND u.deleted = false")
    List<User> findUsersWithVerifiedContacts();
    
    /**
     * Count active users by tenant
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.tenantId = :tenantId AND u.status = 'ACTIVE' AND u.deleted = false")
    long countActiveUsersByTenant(@Param("tenantId") UUID tenantId);
}
