package com.fabricmanagement.common.platform.user.infra.repository;

import com.fabricmanagement.common.platform.user.domain.ContactType;
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
 * <p><b>CRITICAL:</b> contactValue is the primary identifier (NO username!)</p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find user by contact value (email or phone).
     * <p>Used for login and registration.</p>
     */
    Optional<User> findByContactValue(String contactValue);

    /**
     * Find user by tenant and ID.
     * <p>Standard tenant-scoped query.</p>
     */
    Optional<User> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Find user by tenant and contact value.
     */
    Optional<User> findByTenantIdAndContactValue(UUID tenantId, String contactValue);

    /**
     * Get all active users for a tenant.
     */
    List<User> findByTenantIdAndIsActiveTrue(UUID tenantId);

    /**
     * Get all users for a company.
     */
    List<User> findByTenantIdAndCompanyId(UUID tenantId, UUID companyId);

    /**
     * Get all active users for a company.
     */
    List<User> findByTenantIdAndCompanyIdAndIsActiveTrue(UUID tenantId, UUID companyId);

    /**
     * Get users by department.
     */
    List<User> findByTenantIdAndDepartment(UUID tenantId, String department);

    /**
     * Get users by contact type.
     */
    List<User> findByTenantIdAndContactType(UUID tenantId, ContactType contactType);

    /**
     * Check if contact value exists (for registration validation).
     */
    boolean existsByContactValue(String contactValue);

    /**
     * Check if user exists in tenant.
     */
    boolean existsByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Search users by name.
     */
    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND (u.firstName LIKE %:search% OR u.lastName LIKE %:search% OR u.displayName LIKE %:search%)")
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

