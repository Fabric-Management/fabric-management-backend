package com.fabricmanagement.platform.realtime.infra.repository;

import com.fabricmanagement.platform.realtime.domain.LiveResource;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The lease control that is not a row of {@code live_edit_lease} (CEDIT-07): the per-resource
 * transaction lock that serialises every decision which may grant a lease or rely on nobody holding
 * one (acquiring, and other writers checking that a field is free). Leases are always enforced, so
 * there is no stored mode (CEDIT-07-F3).
 *
 * <p>Bound to the primary DataSource, so it runs on the caller's transaction connection and ends
 * with it.
 */
@Repository
public class LiveEditLeaseControlRepository {

  /** A transaction-scoped advisory lock, released when the transaction ends. */
  static final String LOCK_SQL = "select pg_advisory_xact_lock(hashtextextended(?, 0))";

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
}
