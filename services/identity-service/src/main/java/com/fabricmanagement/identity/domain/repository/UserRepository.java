package com.fabricmanagement.identity.domain.repository;

import com.fabricmanagement.identity.domain.model.User;
import com.fabricmanagement.identity.domain.valueobject.UserId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User entities.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Finds user by user ID.
     */
    Optional<User> findByUserId(UUID userId);
    
    /**
     * Finds user by username.
     */
    Optional<User> findByUsername(String username);
    
    /**
     * Finds user by email.
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Finds user by username or email.
     */
    @Query("SELECT u FROM User u WHERE u.username = :usernameOrEmail OR u.email = :usernameOrEmail")
    Optional<User> findByUsernameOrEmail(@Param("usernameOrEmail") String usernameOrEmail);
    
    /**
     * Finds users by tenant ID.
     */
    List<User> findByTenantId(UUID tenantId);
    
    /**
     * Finds active users by tenant ID.
     */
    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND u.isActive = true AND u.isDeleted = false")
    List<User> findActiveByTenantId(@Param("tenantId") UUID tenantId);
    
    /**
     * Finds users by role.
     */
    List<User> findByRole(String role);
    
    /**
     * Finds users by status.
     */
    List<User> findByStatus(String status);
    
    /**
     * Checks if user exists by username.
     */
    boolean existsByUsername(String username);
    
    /**
     * Checks if user exists by email.
     */
    boolean existsByEmail(String email);
    
    /**
     * Checks if user exists by username or email.
     */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.username = :usernameOrEmail OR u.email = :usernameOrEmail")
    boolean existsByUsernameOrEmail(@Param("usernameOrEmail") String usernameOrEmail);
    
    /**
     * Finds users by tenant ID and role.
     */
    List<User> findByTenantIdAndRole(UUID tenantId, String role);
    
    /**
     * Finds users by tenant ID and status.
     */
    List<User> findByTenantIdAndStatus(UUID tenantId, String status);
    
    /**
     * Searches users by keyword.
     */
    @Query("SELECT u FROM User u WHERE " +
           "(u.firstName LIKE %:keyword% OR " +
           "u.lastName LIKE %:keyword% OR " +
           "u.username LIKE %:keyword% OR " +
           "u.email LIKE %:keyword%) AND " +
           "u.isActive = true AND u.isDeleted = false")
    List<User> searchByKeyword(@Param("keyword") String keyword);
    
    /**
     * Finds users by tenant ID and keyword.
     */
    @Query("SELECT u FROM User u WHERE " +
           "u.tenantId = :tenantId AND " +
           "(u.firstName LIKE %:keyword% OR " +
           "u.lastName LIKE %:keyword% OR " +
           "u.username LIKE %:keyword% OR " +
           "u.email LIKE %:keyword%) AND " +
           "u.isActive = true AND u.isDeleted = false")
    List<User> searchByTenantIdAndKeyword(@Param("tenantId") UUID tenantId, @Param("keyword") String keyword);
}