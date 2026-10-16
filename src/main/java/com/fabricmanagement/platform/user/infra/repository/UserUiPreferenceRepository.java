package com.fabricmanagement.platform.user.infra.repository;

import com.fabricmanagement.platform.user.domain.UserUiPreference;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link UserUiPreference}.
 *
 * <p><b>CRITICAL:</b> All queries MUST be tenant-scoped. Always pass {@code
 * TenantContext.requireTenantId()} as the first argument — never query without it.
 */
@Repository
public interface UserUiPreferenceRepository extends JpaRepository<UserUiPreference, UUID> {

  /**
   * The user's preference under one key.
   *
   * @param tenantId must be from TenantContext.requireTenantId()
   * @param userId the user id (same tenant)
   * @param preferenceKey the screen-owned key
   * @return the stored preference; empty if it was never saved
   */
  Optional<UserUiPreference> findByTenantIdAndUser_IdAndPreferenceKey(
      UUID tenantId, UUID userId, String preferenceKey);

  /**
   * Creates the preference or replaces its values in one statement and returns the stored row. A
   * concurrent save of the same key waits for the other and then replaces it; the last save wins.
   * Native SQL bypasses the entity callbacks, so every identifier, audit, soft-delete and version
   * column is written here. Intentionally not {@code @Modifying}: PostgreSQL RETURNING produces an
   * entity result, not an update count.
   */
  @Query(
      value =
          """
          INSERT INTO common_user.user_ui_preference
              (id, tenant_id, uid, user_id, preference_key, preference_values,
               created_at, created_by, updated_at, updated_by, is_active, deleted_at, version)
          VALUES
              (:id, :tenantId, :uid, :userId, :preferenceKey, CAST(:preferenceValues AS jsonb),
               :now, :actorId, :now, :actorId, TRUE, NULL, 0)
          ON CONFLICT ON CONSTRAINT uk_user_ui_preference_tenant_user_key DO UPDATE SET
              preference_values = EXCLUDED.preference_values,
              updated_at = EXCLUDED.updated_at,
              updated_by = EXCLUDED.updated_by,
              version = common_user.user_ui_preference.version + 1
          RETURNING *
          """,
      nativeQuery = true)
  UserUiPreference upsert(
      @Param("tenantId") UUID tenantId,
      @Param("userId") UUID userId,
      @Param("preferenceKey") String preferenceKey,
      @Param("id") UUID id,
      @Param("uid") String uid,
      @Param("preferenceValues") String preferenceValues,
      @Param("now") Instant now,
      @Param("actorId") UUID actorId);
}
