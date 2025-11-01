package com.fabricmanagement.common.platform.auth.infra.repository;

import com.fabricmanagement.common.platform.auth.domain.AuthUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for AuthUser entity.
 *
 * <p>Now uses Contact entity for authentication (new communication system).</p>
 */
@Repository
public interface AuthUserRepository extends JpaRepository<AuthUser, UUID> {

    /**
     * Find AuthUser by Contact entity ID (new system - recommended).
     */
    @Query("SELECT au FROM AuthUser au LEFT JOIN FETCH au.contact WHERE au.contactId = :contactId")
    Optional<AuthUser> findByContactId(@Param("contactId") UUID contactId);

    boolean existsByContactId(UUID contactId);

    /**
     * Find AuthUser by contact value via Contact entity (new system - recommended).
     * <p>Uses JOIN to Contact entity to find by contactValue.</p>
     */
    @Query("SELECT au FROM AuthUser au " +
           "LEFT JOIN FETCH au.contact " +
           "JOIN com.fabricmanagement.common.platform.communication.domain.Contact c ON au.contactId = c.id " +
           "WHERE c.contactValue = :contactValue")
    Optional<AuthUser> findByContactValue(@Param("contactValue") String contactValue);

    /**
     * Check if AuthUser exists by contact value via Contact entity (new system).
     */
    @Query("SELECT COUNT(au) > 0 FROM AuthUser au " +
           "JOIN com.fabricmanagement.common.platform.communication.domain.Contact c ON au.contactId = c.id " +
           "WHERE c.contactValue = :contactValue")
    boolean existsByContactValue(@Param("contactValue") String contactValue);
}
