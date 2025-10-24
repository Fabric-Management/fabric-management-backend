package com.fabricmanagement.common.platform.auth.infra.repository;

import com.fabricmanagement.common.platform.auth.domain.AuthUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for AuthUser entity.
 */
@Repository
public interface AuthUserRepository extends JpaRepository<AuthUser, UUID> {

    Optional<AuthUser> findByContactValue(String contactValue);

    Optional<AuthUser> findByTenantIdAndContactValue(UUID tenantId, String contactValue);

    boolean existsByContactValue(String contactValue);
}

