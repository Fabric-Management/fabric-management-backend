package com.fabricmanagement.common.platform.user.infra.repository;

import com.fabricmanagement.common.platform.user.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for User entity.
 *
 * <p><b>CRITICAL:</b> All queries are tenant-scoped for multi-tenant isolation.
 *
 * <p><b>CRITICAL:</b> Users are identified via Contact entity (UserContact junction)
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

  /**
   * Find user by contact value via Contact entity (new system - recommended).
   *
   * <p>Uses UserContact junction table and Contact entity.
   *
   * <p>Eagerly fetches Role to avoid lazy loading issues.
   *
   * <p><b>CRITICAL:</b> Tenant isolation enforced via Contact's tenant_id.
   */
  @Query(
      "SELECT DISTINCT u FROM User u "
          + "LEFT JOIN FETCH u.role "
          + "JOIN com.fabricmanagement.common.platform.user.domain.UserContact uc ON u.id = uc.userId "
          + "JOIN com.fabricmanagement.common.platform.communication.domain.Contact c ON uc.contactId = c.id "
          + "WHERE c.contactValue = :contactValue "
          + "AND u.tenantId = c.tenantId")
  Optional<User> findByContactValue(@Param("contactValue") String contactValue);

  /**
   * Find user by tenant and ID.
   *
   * <p>Standard tenant-scoped query.
   *
   * <p>Eagerly fetches Role to avoid lazy loading issues.
   */
  @Query("SELECT u FROM User u LEFT JOIN FETCH u.role WHERE u.tenantId = :tenantId AND u.id = :id")
  Optional<User> findByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

  /**
   * Find user by tenant and contact value via Contact entity (new system).
   *
   * <p>Uses UserContact junction table and Contact entity.
   *
   * <p>Eagerly fetches Role and UserContacts to avoid lazy loading issues.
   */
  @Query(
      "SELECT DISTINCT u FROM User u "
          + "LEFT JOIN FETCH u.role "
          + "LEFT JOIN FETCH u.userContacts uc "
          + "LEFT JOIN FETCH uc.contact "
          + "JOIN UserContact ucFilter ON u.id = ucFilter.userId "
          + "JOIN com.fabricmanagement.common.platform.communication.domain.Contact c ON ucFilter.contactId = c.id "
          + "WHERE u.tenantId = :tenantId AND c.contactValue = :contactValue")
  Optional<User> findByTenantIdAndContactValue(
      @Param("tenantId") UUID tenantId, @Param("contactValue") String contactValue);

  /**
   * Get all active users for a tenant.
   *
   * <p>Eagerly fetches Role to avoid lazy loading issues.
   */
  @Query(
      "SELECT u FROM User u LEFT JOIN FETCH u.role WHERE u.tenantId = :tenantId AND u.isActive = true")
  List<User> findByTenantIdAndIsActiveTrue(@Param("tenantId") UUID tenantId);

  /**
   * Get all users for an organization.
   *
   * <p>Eagerly fetches Role to avoid lazy loading issues.
   */
  @Query(
      "SELECT u FROM User u LEFT JOIN FETCH u.role WHERE u.tenantId = :tenantId AND u.organizationId = :organizationId")
  List<User> findByTenantIdAndOrganizationId(
      @Param("tenantId") UUID tenantId, @Param("organizationId") UUID organizationId);

  /**
   * Get all active users for an organization.
   *
   * <p>Eagerly fetches Role to avoid lazy loading issues.
   */
  @Query(
      "SELECT u FROM User u LEFT JOIN FETCH u.role WHERE u.tenantId = :tenantId AND u.organizationId = :organizationId AND u.isActive = true")
  List<User> findByTenantIdAndOrganizationIdAndIsActiveTrue(
      @Param("tenantId") UUID tenantId, @Param("organizationId") UUID organizationId);

  /**
   * Check if contact value exists via Contact entity (new system).
   *
   * <p>Uses UserContact junction table and Contact entity.
   *
   * <p><b>CRITICAL:</b> Tenant isolation enforced via Contact's tenant_id.
   */
  @Query(
      "SELECT COUNT(u) > 0 FROM User u "
          + "JOIN com.fabricmanagement.common.platform.user.domain.UserContact uc ON u.id = uc.userId "
          + "JOIN com.fabricmanagement.common.platform.communication.domain.Contact c ON uc.contactId = c.id "
          + "WHERE c.contactValue = :contactValue "
          + "AND u.tenantId = c.tenantId")
  boolean existsByContactValue(@Param("contactValue") String contactValue);

  /** Check if user exists in tenant. */
  boolean existsByTenantIdAndId(UUID tenantId, UUID id);

  /**
   * Check if contact value exists in tenant (for enumeration protection — tenant-scoped only).
   *
   * @param tenantId Tenant ID
   * @param contactValue Contact value (email or phone)
   * @return true if any user in tenant has this contact value
   */
  @Query(
      "SELECT COUNT(u) > 0 FROM User u "
          + "JOIN com.fabricmanagement.common.platform.user.domain.UserContact uc ON u.id = uc.userId "
          + "JOIN com.fabricmanagement.common.platform.communication.domain.Contact c ON uc.contactId = c.id "
          + "WHERE u.tenantId = :tenantId AND c.contactValue = :contactValue")
  boolean existsByTenantIdAndContactValue(
      @Param("tenantId") UUID tenantId, @Param("contactValue") String contactValue);

  /**
   * Search users by name.
   *
   * <p>Eagerly fetches Role to avoid lazy loading issues.
   */
  @Query(
      "SELECT u FROM User u LEFT JOIN FETCH u.role WHERE u.tenantId = :tenantId AND (u.firstName LIKE %:search% OR u.lastName LIKE %:search% OR u.displayName LIKE %:search%)")
  List<User> searchByName(@Param("tenantId") UUID tenantId, @Param("search") String search);

  /** Count active users in tenant. */
  long countByTenantIdAndIsActiveTrue(UUID tenantId);

  /** Count users in organization. */
  long countByTenantIdAndOrganizationId(UUID tenantId, UUID organizationId);

  /**
   * Find any user with contacts in the given email domain. Used for providing context-aware error
   * messages during login.
   *
   * <p><b>Note:</b> This is a cross-tenant check (not tenant-scoped) because we want to find any
   * user with this domain for better error message context.
   *
   * @param domain Email domain (e.g., "gmail.com", "company.com")
   * @return Optional user if found (returns first match)
   */
  @Query(
      "SELECT DISTINCT u FROM User u "
          + "LEFT JOIN FETCH u.role "
          + "JOIN UserContact uc ON u.id = uc.userId "
          + "JOIN com.fabricmanagement.common.platform.communication.domain.Contact c ON uc.contactId = c.id "
          + "WHERE c.contactType = 'EMAIL' "
          + "AND c.contactValue LIKE CONCAT('%@', :domain) "
          + "ORDER BY u.createdAt ASC")
  Optional<User> findAnyByEmailDomain(@Param("domain") String domain);
}
