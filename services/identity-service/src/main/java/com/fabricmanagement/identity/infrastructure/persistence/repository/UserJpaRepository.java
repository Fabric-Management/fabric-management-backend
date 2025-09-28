package com.fabricmanagement.identity.infrastructure.persistence.repository;

import com.fabricmanagement.identity.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA Repository for UserEntity.
 */
@Repository
public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByUsername(String username);

    @Query("SELECT u FROM UserEntity u JOIN u.contacts c WHERE c.contactValue = :email AND c.contactType = 'EMAIL'")
    Optional<UserEntity> findByEmail(@Param("email") String email);

    @Query("SELECT u FROM UserEntity u JOIN u.contacts c WHERE c.contactValue = :contactValue")
    Optional<UserEntity> findByContactValue(@Param("contactValue") String contactValue);

    boolean existsByUsername(String username);

    @Query("SELECT COUNT(u) > 0 FROM UserEntity u JOIN u.contacts c WHERE c.contactValue = :email AND c.contactType = 'EMAIL'")
    boolean existsByEmail(@Param("email") String email);

    @Query("SELECT COUNT(u) > 0 FROM UserEntity u JOIN u.contacts c WHERE c.contactValue = :contactValue")
    boolean existsByContactValue(@Param("contactValue") String contactValue);

    @Query("SELECT u FROM UserEntity u WHERE u.tenantId = :tenantId AND u.deleted = false")
    java.util.List<UserEntity> findByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT u FROM UserEntity u WHERE u.status = :status AND u.deleted = false")
    java.util.List<UserEntity> findByStatus(@Param("status") com.fabricmanagement.identity.domain.valueobject.UserStatus status);
}