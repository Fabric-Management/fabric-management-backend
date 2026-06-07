package com.fabricmanagement.platform.user.infra.repository;

import com.fabricmanagement.platform.user.domain.UserNavPreferences;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for UserNavPreferences entity.
 *
 * <p><b>CRITICAL:</b> All queries MUST be tenant-scoped. Always pass {@code
 * TenantContext.requireTenantId()} as the first argument — never query without it.
 */
@Repository
public interface UserNavPreferencesRepository extends JpaRepository<UserNavPreferences, UUID> {

  /**
   * Find preferences by tenant and user.
   *
   * <p>Callers must pass {@link
   * com.fabricmanagement.common.infrastructure.persistence.TenantContext.requireTenantId()} for
   * {@code tenantId} to enforce tenant isolation.
   *
   * @param tenantId must be from TenantContext.requireTenantId()
   * @param userId the user id (same tenant)
   * @return optional preferences row; empty if none exists yet
   */
  Optional<UserNavPreferences> findByTenantIdAndUser_Id(UUID tenantId, UUID userId);
}
