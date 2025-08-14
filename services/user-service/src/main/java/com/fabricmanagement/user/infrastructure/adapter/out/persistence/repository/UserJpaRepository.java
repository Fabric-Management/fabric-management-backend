package com.fabricmanagement.user.infrastructure.adapter.out.persistence.repository;

import com.fabricmanagement.user.infrastructure.adapter.out.persistence.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<UserJpaEntity> findByUsername(String username);

    Optional<UserJpaEntity> findByUsernameAndTenantId(String username, UUID tenantId);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndTenantId(String username, UUID tenantId);

    List<UserJpaEntity> findByTenantId(UUID tenantId);

    @Query("SELECT u FROM UserJpaEntity u WHERE u.tenantId = :tenantId AND u.deleted = false")
    List<UserJpaEntity> findActiveUsersByTenantId(@Param("tenantId") UUID tenantId);
}