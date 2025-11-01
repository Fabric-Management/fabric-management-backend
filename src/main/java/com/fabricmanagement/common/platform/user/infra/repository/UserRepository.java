package com.fabricmanagement.common.platform.user.infra.repository;

import com.fabricmanagement.common.platform.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User entity.
 *
 * <p><b>CRITICAL:</b> All queries are tenant-scoped for multi-tenant isolation.</p>
 * <p><b>CRITICAL:</b> Users are identified via Contact entity (UserContact junction)</p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find user by contact value via Contact entity (new system - recommended).
     * <p>Uses UserContact junction table and Contact entity.</p>
     * <p>Eagerly fetches Role to avoid lazy loading issues.</p>
     */
    @Query("SELECT DISTINCT u FROM User u " +
           "LEFT JOIN FETCH u.role " +
           "JOIN com.fabricmanagement.common.platform.communication.domain.UserContact uc ON u.id = uc.userId " +
           "JOIN com.fabricmanagement.common.platform.communication.domain.Contact c ON uc.contactId = c.id " +
           "WHERE c.contactValue = :contactValue AND uc.isForAuthentication = true")
    Optional<User> findByContactValue(@Param("contactValue") String contactValue);

    /**
     * Find user by tenant and ID.
     * <p>Standard tenant-scoped query.</p>
     * <p>Eagerly fetches Role to avoid lazy loading issues.</p>
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.role WHERE u.tenantId = :tenantId AND u.id = :id")
    Optional<User> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /**
     * Find user by tenant and contact value via Contact entity (new system).
     * <p>Uses UserContact junction table and Contact entity.</p>
     * <p>Eagerly fetches Role to avoid lazy loading issues.</p>
     */
    @Query("SELECT DISTINCT u FROM User u " +
           "LEFT JOIN FETCH u.role " +
           "JOIN com.fabricmanagement.common.platform.communication.domain.UserContact uc ON u.id = uc.userId " +
           "JOIN com.fabricmanagement.common.platform.communication.domain.Contact c ON uc.contactId = c.id " +
           "WHERE u.tenantId = :tenantId AND c.contactValue = :contactValue AND uc.isForAuthentication = true")
    Optional<User> findByTenantIdAndContactValue(@Param("tenantId") UUID tenantId, @Param("contactValue") String contactValue);

    /**
     * Get all active users for a tenant.
     * <p>Eagerly fetches Role to avoid lazy loading issues.</p>
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.role WHERE u.tenantId = :tenantId AND u.isActive = true")
    List<User> findByTenantIdAndIsActiveTrue(@Param("tenantId") UUID tenantId);

    /**
     * Get all users for a company.
     * <p>Eagerly fetches Role to avoid lazy loading issues.</p>
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.role WHERE u.tenantId = :tenantId AND u.companyId = :companyId")
    List<User> findByTenantIdAndCompanyId(@Param("tenantId") UUID tenantId, @Param("companyId") UUID companyId);

    /**
     * Get all active users for a company.
     * <p>Eagerly fetches Role to avoid lazy loading issues.</p>
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.role WHERE u.tenantId = :tenantId AND u.companyId = :companyId AND u.isActive = true")
    List<User> findByTenantIdAndCompanyIdAndIsActiveTrue(@Param("tenantId") UUID tenantId, @Param("companyId") UUID companyId);

    /**
     * Check if contact value exists via Contact entity (new system).
     * <p>Uses UserContact junction table and Contact entity.</p>
     */
    @Query("SELECT COUNT(u) > 0 FROM User u " +
           "JOIN com.fabricmanagement.common.platform.communication.domain.UserContact uc ON u.id = uc.userId " +
           "JOIN com.fabricmanagement.common.platform.communication.domain.Contact c ON uc.contactId = c.id " +
           "WHERE c.contactValue = :contactValue AND uc.isForAuthentication = true")
    boolean existsByContactValue(@Param("contactValue") String contactValue);

    /**
     * Check if user exists in tenant.
     */
    boolean existsByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Search users by name.
     * <p>Eagerly fetches Role to avoid lazy loading issues.</p>
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.role WHERE u.tenantId = :tenantId AND (u.firstName LIKE %:search% OR u.lastName LIKE %:search% OR u.displayName LIKE %:search%)")
    List<User> searchByName(@Param("tenantId") UUID tenantId, @Param("search") String search);

    /**
     * Count active users in tenant.
     */
    long countByTenantIdAndIsActiveTrue(UUID tenantId);

    /**
     * Count users in company.
     */
    long countByTenantIdAndCompanyId(UUID tenantId, UUID companyId);
}

