package com.fabricmanagement.common.infrastructure.scheduling;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;

/**
 * Lets one application instance at a time run a periodic job for a tenant, however many instances
 * run.
 *
 * <p>Two instances doing the same tenant's work would do it twice for nothing; the jobs that use
 * this are written so that they could not corrupt anything doing so, and the lease only saves the
 * cost. It is a PostgreSQL advisory lock scoped to a transaction that is kept open, and does
 * nothing else, on a connection of its own for as long as the run lasts. Ending that transaction
 * releases the lock, and so does losing the connection or the instance, so a crash can never leave
 * a tenant locked out.
 *
 * <p>The job reads and writes on other connections; it needs no tenant context for this. Each job
 * extends this with a key of its own, so that jobs never hold each other's leases.
 */
@Slf4j
public abstract class TenantRunLease {
  private final DataSource dataSource;
  private final String key;

  /**
   * @param key the job's name; distinct jobs must use distinct keys
   */
  protected TenantRunLease(DataSource dataSource, String key) {
    this.dataSource = dataSource;
    this.key = key + ":";
  }

  /**
   * Takes the tenant's lease without waiting.
   *
   * @return the held lease, to be closed when the run ends; empty when another run holds it
   */
  public Optional<Hold> tryAcquire(UUID tenantId) {
    Connection connection = null;
    try {
      connection = dataSource.getConnection();
      connection.setAutoCommit(false);
      boolean acquired;
      try (var statement =
          connection.prepareStatement("select pg_try_advisory_xact_lock(hashtextextended(?, 0))")) {
        statement.setString(1, key + tenantId);
        try (var rows = statement.executeQuery()) {
          acquired = rows.next() && rows.getBoolean(1);
        }
      }
      if (!acquired) {
        end(connection);
        return Optional.empty();
      }
      return Optional.of(new Hold(connection));
    } catch (SQLException failure) {
      end(connection);
      throw new IllegalStateException("Could not take the lease " + key, failure);
    }
  }

  private static void end(Connection connection) {
    if (connection == null) return;
    try {
      connection.rollback();
    } catch (SQLException failure) {
      log.debug("Tenant run lease: rollback failed", failure);
    }
    try {
      connection.close();
    } catch (SQLException failure) {
      log.debug("Tenant run lease: close failed", failure);
    }
  }

  /** A lease that is held until closed. */
  public static final class Hold implements AutoCloseable {
    private final Connection connection;

    /** Public so that the tests of a job can hand one out over a connection of their own. */
    public Hold(Connection connection) {
      this.connection = connection;
    }

    @Override
    public void close() {
      end(connection);
    }
  }
}
