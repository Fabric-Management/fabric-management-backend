package com.fabricmanagement.common.platform.communication.infra.repository;

import com.fabricmanagement.common.platform.communication.domain.UserContact;
import com.fabricmanagement.common.platform.communication.domain.UserContactId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for UserContact junction entity. */
@Repository
public interface UserContactRepository extends JpaRepository<UserContact, UserContactId> {

  /**
   * Find all contacts for a user within tenant.
   *
   * <p>Uses JOIN FETCH to avoid N+1 query problem when accessing Contact entities.
   */
  @Query(
      "SELECT uc FROM UserContact uc "
          + "LEFT JOIN FETCH uc.contact "
          + "WHERE uc.tenantId = :tenantId AND uc.userId = :userId")
  List<UserContact> findByTenantIdAndUserId(
      @Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

  /**
   * Find specific user-contact assignment.
   *
   * <p>Uses JOIN FETCH to avoid N+1 query problem when accessing Contact entity.
   */
  @Query(
      "SELECT uc FROM UserContact uc "
          + "LEFT JOIN FETCH uc.contact "
          + "WHERE uc.userId = :userId AND uc.contactId = :contactId")
  Optional<UserContact> findByUserIdAndContactId(
      @Param("userId") UUID userId, @Param("contactId") UUID contactId);

  /**
   * Find default contact for user.
   *
   * <p>Uses JOIN FETCH to avoid N+1 query problem when accessing Contact entity.
   */
  @Query(
      "SELECT uc FROM UserContact uc "
          + "LEFT JOIN FETCH uc.contact "
          + "WHERE uc.userId = :userId AND uc.isDefault = true")
  Optional<UserContact> findDefaultByUserId(@Param("userId") UUID userId);

  /**
   * Find preferred verified contact for authentication.
   *
   * <p>Returns verified contact (any verified contact = authentication contact). Orders by default
   * flag first, then creation time for deterministic behaviour.
   *
   * <p>Uses JOIN FETCH to avoid N+1 query problem when accessing Contact entity.
   */
  @Query(
      "SELECT uc FROM UserContact uc "
          + "LEFT JOIN FETCH uc.contact c "
          + "WHERE uc.userId = :userId AND c.isVerified = true "
          + "ORDER BY uc.isDefault DESC, uc.createdAt ASC")
  Optional<UserContact> findPreferredContactByUserId(@Param("userId") UUID userId);

  /**
   * Check if user-contact assignment exists.
   *
   * <p><b>Performance:</b> Uses EXISTS query instead of loading full entity. Used to prevent N+1
   * problems when checking if contact is already assigned.
   *
   * @param userId the user ID
   * @param contactId the contact ID
   * @return true if assignment exists
   */
  @Query(
      "SELECT CASE WHEN COUNT(uc) > 0 THEN true ELSE false END "
          + "FROM UserContact uc "
          + "WHERE uc.userId = :userId AND uc.contactId = :contactId")
  boolean existsByUserIdAndContactId(
      @Param("userId") UUID userId, @Param("contactId") UUID contactId);

  /**
   * Find all user contacts for a specific contact ID (find users who have this contact).
   *
   * <p>Uses JOIN FETCH to avoid N+1 query problem when accessing Contact entity.
   *
   * @param tenantId Tenant ID
   * @param contactId Contact ID
   * @return List of UserContact assignments
   */
  @Query(
      "SELECT uc FROM UserContact uc "
          + "LEFT JOIN FETCH uc.contact "
          + "WHERE uc.tenantId = :tenantId AND uc.contactId = :contactId")
  List<UserContact> findByTenantIdAndContactId(
      @Param("tenantId") UUID tenantId, @Param("contactId") UUID contactId);
}
