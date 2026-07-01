package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.platform.auth.domain.MfaType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Backfills platform login identities and memberships from existing tenant-scoped AuthUser rows.
 *
 * <p>CRITICAL: Source tables are RLS-protected. This runner executes entirely through {@link
 * SystemTransactionExecutor} (fabric_system BYPASSRLS) and uses JDBC only, mirroring the Finance
 * permission backfill pattern.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginIdentityBackfillRunner {

  private static final String SOURCE_ROWS_SQL =
      """
      SELECT
          au.id AS auth_user_id,
          au.tenant_id,
          au.user_id,
          au.password_hash,
          au.is_mfa_enabled,
          au.primary_mfa_type,
          au.mfa_secret,
          au.is_active,
          au.is_verified AS email_verified,
          au.failed_login_attempts,
          au.locked_until,
          lower(btrim(email_contact.contact_value)) AS email
      FROM common_auth.common_auth_user au
      JOIN common_user.common_user u
        ON u.id = au.user_id
       AND u.tenant_id = au.tenant_id
      LEFT JOIN LATERAL (
          SELECT c.contact_value
          FROM common_user.common_user_contact uc
          JOIN common_communication.common_contact c
            ON c.id = uc.contact_id
           AND c.tenant_id = uc.tenant_id
          WHERE uc.user_id = au.user_id
            AND uc.tenant_id = au.tenant_id
            AND c.contact_type = 'EMAIL'
            AND c.contact_value IS NOT NULL
            AND btrim(c.contact_value) <> ''
            AND (uc.is_default = TRUE OR c.is_verified = TRUE)
          ORDER BY
            CASE WHEN uc.is_default = TRUE THEN 0 ELSE 1 END,
            CASE WHEN c.is_verified = TRUE THEN 0 ELSE 1 END,
            c.created_at ASC,
            c.id ASC
          LIMIT 1
      ) email_contact ON TRUE
      ORDER BY lower(btrim(email_contact.contact_value)) NULLS LAST, au.created_at ASC, au.id ASC
      """;

  private static final String FIND_IDENTITY_SQL =
      """
      SELECT id, requires_password_reset
      FROM common_auth.login_identity
      WHERE email = ?
      """;

  private static final String INSERT_IDENTITY_SQL =
      """
      INSERT INTO common_auth.login_identity (
          id, email, password_hash, is_mfa_enabled, primary_mfa_type, mfa_secret, is_active,
          email_verified, failed_login_attempts, locked_until, requires_password_reset, created_at,
          updated_at
      )
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, NOW(), NOW())
      """;

  private static final String MEMBERSHIP_EXISTS_BY_USER_SQL =
      """
      SELECT COUNT(*) > 0
      FROM common_auth.membership
      WHERE user_id = ?
      """;

  private static final String INSERT_MEMBERSHIP_SQL =
      """
      INSERT INTO common_auth.membership (
          id, login_identity_id, tenant_id, user_id, status, is_default, created_at, updated_at
      )
      VALUES (?, ?, ?, ?, 'ACTIVE', ?, NOW(), NOW())
      ON CONFLICT DO NOTHING
      """;

  private static final String MARK_REQUIRES_PASSWORD_RESET_SQL =
      """
      UPDATE common_auth.login_identity
      SET requires_password_reset = TRUE,
          updated_at = NOW()
      WHERE id = ?
        AND requires_password_reset = FALSE
      """;

  private final SystemTransactionExecutor systemTransactionExecutor;

  @EventListener(ApplicationReadyEvent.class)
  public void runBackfill() {
    log.info("Starting LoginIdentityBackfillRunner cross-tenant backfill...");

    try {
      BackfillStats stats =
          systemTransactionExecutor.executeInTransaction(
              jdbcTemplate -> {
                List<AuthUserIdentitySource> sourceRows =
                    jdbcTemplate.query(SOURCE_ROWS_SQL, LoginIdentityBackfillRunner::mapSourceRow);
                return backfillRows(sourceRows, new JdbcIdentityBackfillGateway(jdbcTemplate));
              });

      log.info(
          "LoginIdentityBackfillRunner completed. identities={}, memberships={}, collisions={}, "
              + "existingMemberships={}, missingEmail={}",
          stats.createdIdentities(),
          stats.createdMemberships(),
          stats.collisions(),
          stats.existingMemberships(),
          stats.missingEmailSkipped());
    } catch (Exception e) {
      log.error("CRITICAL: Failed to execute LoginIdentityBackfillRunner.", e);
      throw new IllegalStateException(
          "LoginIdentityBackfillRunner failed during cross-tenant backfill", e);
    }
  }

  BackfillStats backfillRows(
      List<AuthUserIdentitySource> sourceRows, IdentityBackfillGateway gateway) {
    int createdIdentities = 0;
    int createdMemberships = 0;
    int collisions = 0;
    int existingMemberships = 0;
    int missingEmailSkipped = 0;

    for (AuthUserIdentitySource source : sourceRows) {
      String email = normalizeEmail(source.email());
      if (email == null) {
        missingEmailSkipped++;
        log.warn(
            "Skipping LoginIdentity backfill for authUserId={} userId={} tenantId={} because no "
                + "default or verified EMAIL contact exists.",
            source.authUserId(),
            source.userId(),
            source.tenantId());
        continue;
      }

      if (gateway.membershipExistsByUserId(source.userId())) {
        existingMemberships++;
        continue;
      }

      Optional<IdentityRow> existingIdentity = gateway.findIdentityByEmail(email);
      UUID identityId;
      boolean defaultMembership;

      if (existingIdentity.isPresent()) {
        identityId = existingIdentity.get().id();
        defaultMembership = false;
        collisions++;
        gateway.markRequiresPasswordReset(identityId);
        log.warn(
            "LoginIdentity collision for email={} authUserId={} tenantId={} userId={}; adding "
                + "membership and requiring password reset.",
            email,
            source.authUserId(),
            source.tenantId(),
            source.userId());
      } else {
        identityId = gateway.insertIdentity(source, email);
        defaultMembership = true;
        createdIdentities++;
      }

      if (gateway.insertMembership(identityId, source, defaultMembership)) {
        createdMemberships++;
      }
    }

    return new BackfillStats(
        createdIdentities,
        createdMemberships,
        collisions,
        existingMemberships,
        missingEmailSkipped);
  }

  private static AuthUserIdentitySource mapSourceRow(ResultSet rs, int rowNum) throws SQLException {
    return new AuthUserIdentitySource(
        rs.getObject("auth_user_id", UUID.class),
        rs.getObject("tenant_id", UUID.class),
        rs.getObject("user_id", UUID.class),
        rs.getString("email"),
        rs.getString("password_hash"),
        rs.getBoolean("is_mfa_enabled"),
        parseMfaType(rs.getString("primary_mfa_type"), rs.getObject("auth_user_id", UUID.class)),
        rs.getString("mfa_secret"),
        rs.getBoolean("is_active"),
        rs.getBoolean("email_verified"),
        rs.getInt("failed_login_attempts"),
        toInstant(rs.getTimestamp("locked_until")));
  }

  private static Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }

  private static Timestamp toTimestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }

  private static MfaType parseMfaType(String value, UUID authUserId) {
    if (value == null || value.isBlank()) {
      return MfaType.NONE;
    }

    try {
      return MfaType.valueOf(value);
    } catch (IllegalArgumentException e) {
      log.warn(
          "Unknown primary_mfa_type={} for authUserId={}; defaulting LoginIdentity MFA type to NONE.",
          value,
          authUserId);
      return MfaType.NONE;
    }
  }

  private static String normalizeEmail(String email) {
    if (email == null || email.isBlank()) {
      return null;
    }
    return email.trim().toLowerCase(Locale.ROOT);
  }

  interface IdentityBackfillGateway {
    Optional<IdentityRow> findIdentityByEmail(String email);

    UUID insertIdentity(AuthUserIdentitySource source, String email);

    boolean membershipExistsByUserId(UUID userId);

    boolean insertMembership(
        UUID identityId, AuthUserIdentitySource source, boolean defaultMembership);

    void markRequiresPasswordReset(UUID identityId);
  }

  record AuthUserIdentitySource(
      UUID authUserId,
      UUID tenantId,
      UUID userId,
      String email,
      String passwordHash,
      boolean mfaEnabled,
      MfaType primaryMfaType,
      String mfaSecret,
      boolean active,
      boolean emailVerified,
      int failedLoginAttempts,
      Instant lockedUntil) {}

  record IdentityRow(UUID id, boolean requiresPasswordReset) {}

  record BackfillStats(
      int createdIdentities,
      int createdMemberships,
      int collisions,
      int existingMemberships,
      int missingEmailSkipped) {}

  private static final class JdbcIdentityBackfillGateway implements IdentityBackfillGateway {

    private final JdbcTemplate jdbcTemplate;

    private JdbcIdentityBackfillGateway(JdbcTemplate jdbcTemplate) {
      this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<IdentityRow> findIdentityByEmail(String email) {
      return jdbcTemplate
          .query(
              FIND_IDENTITY_SQL,
              (rs, rowNum) ->
                  new IdentityRow(
                      rs.getObject("id", UUID.class), rs.getBoolean("requires_password_reset")),
              email)
          .stream()
          .findFirst();
    }

    @Override
    public UUID insertIdentity(AuthUserIdentitySource source, String email) {
      UUID identityId = UUID.randomUUID();
      jdbcTemplate.update(
          INSERT_IDENTITY_SQL,
          identityId,
          email,
          source.passwordHash(),
          source.mfaEnabled(),
          source.primaryMfaType().name(),
          source.mfaSecret(),
          source.active(),
          source.emailVerified(),
          source.failedLoginAttempts(),
          toTimestamp(source.lockedUntil()));
      return identityId;
    }

    @Override
    public boolean membershipExistsByUserId(UUID userId) {
      Boolean exists =
          jdbcTemplate.queryForObject(MEMBERSHIP_EXISTS_BY_USER_SQL, Boolean.class, userId);
      return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean insertMembership(
        UUID identityId, AuthUserIdentitySource source, boolean defaultMembership) {
      int rows =
          jdbcTemplate.update(
              INSERT_MEMBERSHIP_SQL,
              UUID.randomUUID(),
              identityId,
              source.tenantId(),
              source.userId(),
              defaultMembership);
      return rows > 0;
    }

    @Override
    public void markRequiresPasswordReset(UUID identityId) {
      jdbcTemplate.update(MARK_REQUIRES_PASSWORD_RESET_SQL, identityId);
    }
  }
}
