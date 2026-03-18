package com.fabricmanagement.common.platform.user.infra.repository;

import com.fabricmanagement.common.platform.user.domain.UserContact;
import com.fabricmanagement.common.platform.user.domain.UserContactId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for UserContact junction entity (User module). */
@Repository
public interface UserContactRepository extends JpaRepository<UserContact, UserContactId> {

  @Query(
      "SELECT uc FROM UserContact uc "
          + "LEFT JOIN FETCH uc.contact "
          + "WHERE uc.tenantId = :tenantId AND uc.userId = :userId")
  List<UserContact> findByTenantIdAndUserId(
      @Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

  @Query(
      "SELECT uc FROM UserContact uc "
          + "LEFT JOIN FETCH uc.contact "
          + "WHERE uc.userId = :userId AND uc.contactId = :contactId")
  Optional<UserContact> findByUserIdAndContactId(
      @Param("userId") UUID userId, @Param("contactId") UUID contactId);

  @Query(
      "SELECT uc FROM UserContact uc "
          + "LEFT JOIN FETCH uc.contact "
          + "WHERE uc.userId = :userId AND uc.isDefault = true")
  Optional<UserContact> findDefaultByUserId(@Param("userId") UUID userId);

  @Query(
      "SELECT uc FROM UserContact uc "
          + "LEFT JOIN FETCH uc.contact c "
          + "WHERE uc.userId = :userId AND c.isVerified = true "
          + "ORDER BY uc.isDefault DESC, uc.createdAt ASC")
  Optional<UserContact> findPreferredContactByUserId(@Param("userId") UUID userId);

  @Query(
      "SELECT CASE WHEN COUNT(uc) > 0 THEN true ELSE false END "
          + "FROM UserContact uc "
          + "WHERE uc.userId = :userId AND uc.contactId = :contactId")
  boolean existsByUserIdAndContactId(
      @Param("userId") UUID userId, @Param("contactId") UUID contactId);

  @Query(
      "SELECT uc FROM UserContact uc "
          + "LEFT JOIN FETCH uc.contact "
          + "WHERE uc.tenantId = :tenantId AND uc.contactId = :contactId")
  List<UserContact> findByTenantIdAndContactId(
      @Param("tenantId") UUID tenantId, @Param("contactId") UUID contactId);

  @Query(
      "SELECT uc FROM UserContact uc "
          + "LEFT JOIN FETCH uc.contact "
          + "WHERE uc.userId = :userId")
  List<UserContact> findAllByUserId(@Param("userId") UUID userId);
}
