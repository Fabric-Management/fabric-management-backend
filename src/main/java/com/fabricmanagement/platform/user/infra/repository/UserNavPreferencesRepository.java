package com.fabricmanagement.platform.user.infra.repository;

import com.fabricmanagement.platform.user.domain.UserNavPreferences;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

  /**
   * Returns the inserted row, or no row if another preference already exists. Intentionally not
   * {@code @Modifying}: PostgreSQL RETURNING produces an entity result, not an update count.
   */
  @Query(
      value =
          """
          INSERT INTO common_user.user_nav_preferences
              (id, tenant_id, uid, user_id, sort_order, hidden_item_ids,
               created_at, created_by, updated_at, updated_by, is_active, deleted_at, version)
          VALUES
              (:id, :tenantId, :uid, :userId, CAST(:sortOrder AS jsonb),
               CAST(:hiddenItemIds AS jsonb), :now, :actorId, :now, :actorId, TRUE, NULL, 0)
          ON CONFLICT ON CONSTRAINT uk_user_nav_preferences_tenant_user DO NOTHING
          RETURNING *
          """,
      nativeQuery = true)
  Optional<UserNavPreferences> insertIfAbsent(
      @Param("tenantId") UUID tenantId,
      @Param("userId") UUID userId,
      @Param("id") UUID id,
      @Param("uid") String uid,
      @Param("sortOrder") String sortOrder,
      @Param("hiddenItemIds") String hiddenItemIds,
      @Param("now") Instant now,
      @Param("actorId") UUID actorId);
}
