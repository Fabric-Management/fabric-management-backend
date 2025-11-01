package com.fabricmanagement.common.platform.communication.infra.repository;

import com.fabricmanagement.common.platform.communication.domain.UserContact;
import com.fabricmanagement.common.platform.communication.domain.UserContactId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UserContact junction entity.
 */
@Repository
public interface UserContactRepository extends JpaRepository<UserContact, UserContactId> {

    /**
     * Find all contacts for a user within tenant.
     */
    @Query("SELECT uc FROM UserContact uc WHERE uc.tenantId = :tenantId AND uc.userId = :userId")
    List<UserContact> findByTenantIdAndUserId(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId);

    /**
     * Find specific user-contact assignment.
     */
    @Query("SELECT uc FROM UserContact uc WHERE uc.userId = :userId AND uc.contactId = :contactId")
    Optional<UserContact> findByUserIdAndContactId(
            @Param("userId") UUID userId,
            @Param("contactId") UUID contactId);

    /**
     * Find default contact for user.
     */
    @Query("SELECT uc FROM UserContact uc WHERE uc.userId = :userId AND uc.isDefault = true")
    Optional<UserContact> findDefaultByUserId(@Param("userId") UUID userId);

    /**
     * Find authentication contact for user.
     */
    @Query("SELECT uc FROM UserContact uc WHERE uc.userId = :userId AND uc.isForAuthentication = true")
    Optional<UserContact> findAuthenticationContactByUserId(@Param("userId") UUID userId);
}

