package com.fabricmanagement.platform.realtime.infra.repository;

import com.fabricmanagement.platform.realtime.domain.LiveResource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The two pieces of lease control that are not rows of {@code live_edit_lease} (CEDIT-07):
 *
 * <ul>
 *   <li>the per-resource transaction lock that serialises every decision which may grant a lease or
 *       rely on nobody holding one (acquiring, and other writers checking that a field is free);
 *   <li>the enforcement mode of a resource type in the bound tenant, one row per enforced type in
 *       {@code common_infrastructure.live_edit_lease_mode}; no row means {@code OFF}.
 * </ul>
 *
 * Bound to the primary DataSource, so both run on the caller's transaction connection with its
 * tenant setting (RLS) and end with it.
 */
@Repository
public class LiveEditLeaseControlRepository {

  /** A transaction-scoped advisory lock, released when the transaction ends. */
  static final String LOCK_SQL = "select pg_advisory_xact_lock(hashtextextended(?, 0))";

  static final String ENFORCED_SQL =
      "select exists (select 1 from common_infrastructure.live_edit_lease_mode"
          + " where tenant_id = ? and resource_type = ?)";

  /** Monotonic: an existing row is kept as it is, so enforcement is never lowered here. */
  static final String ENFORCE_SQL =
      "insert into common_infrastructure.live_edit_lease_mode"
          + " (tenant_id, resource_type, enforced_at, enforced_by)"
          + " values (?, ?, ?, ?) on conflict (tenant_id, resource_type) do nothing";

  private final JdbcTemplate jdbc;

  public LiveEditLeaseControlRepository(@Qualifier("dataSource") DataSource dataSource) {
    this.jdbc = new JdbcTemplate(dataSource);
  }

  /** Serialises lease decisions on one resource until the caller's transaction ends. */
  public void lockResource(UUID tenantId, LiveResource resource) {
    jdbc.query(
        LOCK_SQL,
        (rs, row) -> Boolean.TRUE,
        tenantId + ":LIVE_EDIT_LEASE:" + resource.type() + ":" + resource.id());
  }

  public boolean isEnforced(UUID tenantId, String resourceType) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(ENFORCED_SQL, Boolean.class, tenantId, resourceType));
  }

  /** Records enforcement for the tenant; true when this call switched it on. */
  public boolean enforce(UUID tenantId, String resourceType, Instant at, String by) {
    return jdbc.update(ENFORCE_SQL, tenantId, resourceType, Timestamp.from(at), by) == 1;
  }
}
