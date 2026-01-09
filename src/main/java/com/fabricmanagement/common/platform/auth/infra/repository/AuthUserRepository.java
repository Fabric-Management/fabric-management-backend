package com.fabricmanagement.common.platform.auth.infra.repository;

import com.fabricmanagement.common.platform.auth.domain.AuthUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for AuthUser entity.
 *
 * <p><b>CRITICAL DESIGN CHANGE:</b> User-based authentication (one AuthUser per User).
 *
 * <p>Multi-contact login supported: Any verified contact of a User can be used for login.
 */
@Repository
public interface AuthUserRepository extends JpaRepository<AuthUser, UUID> {

  /**
   * Find AuthUser by User ID (new user-based authentication - recommended).
   *
   * @param userId User ID
   * @return AuthUser if found
   */
  @Query("SELECT au FROM AuthUser au LEFT JOIN FETCH au.user WHERE au.userId = :userId")
  Optional<AuthUser> findByUserId(@Param("userId") UUID userId);

  /**
   * Check if AuthUser exists for User ID.
   *
   * @param userId User ID
   * @return true if AuthUser exists
   */
  boolean existsByUserId(UUID userId);

  /**
   * Find AuthUser by contact value via UserContact junction (new user-based authentication).
   *
   * <p>Finds User via Contact → UserContact → User, then returns User's AuthUser.
   *
   * <p><b>CRITICAL:</b> Tenant isolation enforced via Contact's tenant_id.
   *
   * @param contactValue Contact value (email or phone)
   * @return AuthUser if found
   */
  @Query(
      "SELECT au FROM AuthUser au "
          + "LEFT JOIN FETCH au.user "
          + "JOIN com.fabricmanagement.common.platform.user.domain.User u ON au.userId = u.id "
          + "JOIN com.fabricmanagement.common.platform.communication.domain.UserContact uc ON u.id = uc.userId "
          + "JOIN com.fabricmanagement.common.platform.communication.domain.Contact c ON uc.contactId = c.id "
          + "WHERE c.contactValue = :contactValue "
          + "AND au.tenantId = c.tenantId "
          + "AND u.tenantId = c.tenantId")
  Optional<AuthUser> findByContactValue(@Param("contactValue") String contactValue);

  /**
   * Check if AuthUser exists by contact value (new user-based authentication).
   *
   * <p><b>CRITICAL:</b> Tenant isolation enforced via Contact's tenant_id.
   *
   * @param contactValue Contact value (email or phone)
   * @return true if AuthUser exists
   */
  @Query(
      "SELECT COUNT(au) > 0 FROM AuthUser au "
          + "JOIN com.fabricmanagement.common.platform.user.domain.User u ON au.userId = u.id "
          + "JOIN com.fabricmanagement.common.platform.communication.domain.UserContact uc ON u.id = uc.userId "
          + "JOIN com.fabricmanagement.common.platform.communication.domain.Contact c ON uc.contactId = c.id "
          + "WHERE c.contactValue = :contactValue "
          + "AND au.tenantId = c.tenantId "
          + "AND u.tenantId = c.tenantId")
  boolean existsByContactValue(@Param("contactValue") String contactValue);

  /**
   * Find AuthUser by Contact entity ID (DEPRECATED - for backward compatibility).
   *
   * @deprecated Use {@link #findByUserId(UUID)} or {@link #findByContactValue(String)} instead
   */
  @Deprecated
  @Query("SELECT au FROM AuthUser au LEFT JOIN FETCH au.contact WHERE au.contactId = :contactId")
  Optional<AuthUser> findByContactId(@Param("contactId") UUID contactId);

  /**
   * Check if AuthUser exists by Contact ID (DEPRECATED).
   *
   * @deprecated Use {@link #existsByUserId(UUID)} instead
   */
  @Deprecated
  boolean existsByContactId(UUID contactId);

  /**
   * Check if User has AuthUser (password exists) by checking User's contact IDs.
   *
   * <p>Used for batch checking if a User has any AuthUser via their contacts.
   *
   * <p><b>CRITICAL:</b> Tenant isolation enforced via Contact's tenant_id.
   *
   * @param userId User ID
   * @return true if User has AuthUser
   */
  @Query(
      "SELECT COUNT(au) > 0 FROM AuthUser au "
          + "JOIN com.fabricmanagement.common.platform.user.domain.User u ON au.userId = u.id "
          + "WHERE u.id = :userId "
          + "AND au.tenantId = u.tenantId")
  boolean userHasPassword(@Param("userId") UUID userId);

  /**
   * Find all User IDs that have AuthUser (batch check - avoids N+1).
   *
   * <p><b>CRITICAL:</b> Tenant isolation enforced via User's tenant_id.
   *
   * @param userIds List of User IDs to check
   * @return Set of User IDs that have AuthUser
   */
  @Query(
      "SELECT au.userId FROM AuthUser au "
          + "JOIN com.fabricmanagement.common.platform.user.domain.User u ON au.userId = u.id "
          + "WHERE au.userId IN :userIds "
          + "AND au.tenantId = u.tenantId")
  java.util.Set<UUID> findUserIdsByUserIds(@Param("userIds") java.util.List<UUID> userIds);

  /**
   * Find all AuthUsers by User IDs (batch load - avoids N+1).
   *
   * <p><b>CRITICAL:</b> Tenant isolation enforced via User's tenant_id.
   *
   * @param userIds List of User IDs
   * @return List of AuthUsers (with matching tenant_id)
   */
  @Query(
      "SELECT au FROM AuthUser au "
          + "LEFT JOIN FETCH au.user "
          + "JOIN com.fabricmanagement.common.platform.user.domain.User u ON au.userId = u.id "
          + "WHERE au.userId IN :userIds "
          + "AND au.tenantId = u.tenantId")
  java.util.List<AuthUser> findAllByUserIdIn(@Param("userIds") java.util.List<UUID> userIds);

  /**
   * Find all contact IDs that have AuthUser (DEPRECATED - for backward compatibility).
   *
   * @deprecated Use {@link #findUserIdsByUserIds(java.util.List)} instead
   */
  @Deprecated
  @Query(
      "SELECT au.contactId FROM AuthUser au "
          + "JOIN com.fabricmanagement.common.platform.communication.domain.Contact c ON au.contactId = c.id "
          + "WHERE au.contactId IN :contactIds "
          + "AND au.tenantId = c.tenantId")
  java.util.Set<UUID> findContactIdsByContactIds(
      @Param("contactIds") java.util.List<UUID> contactIds);

  /**
   * Find all AuthUsers by contact IDs (DEPRECATED - for backward compatibility).
   *
   * @deprecated Use {@link #findAllByUserIdIn(java.util.List)} instead
   */
  @Deprecated
  @Query(
      "SELECT au FROM AuthUser au "
          + "LEFT JOIN FETCH au.contact "
          + "JOIN com.fabricmanagement.common.platform.communication.domain.Contact c ON au.contactId = c.id "
          + "WHERE au.contactId IN :contactIds "
          + "AND au.tenantId = c.tenantId")
  java.util.List<AuthUser> findAllByContactIdIn(
      @Param("contactIds") java.util.List<UUID> contactIds);
}
