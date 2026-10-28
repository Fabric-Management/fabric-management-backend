package com.fabricmanagement.platform.tenant.app;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Which tenant row the current transaction is creating, and the JDBC access that shares its
 * connection (ONBOARD-ATOMIC-1).
 *
 * <p>A signup writes its tenant row and everything that hangs off it on the onboarding
 * transaction's own connection, so a failed signup leaves nothing behind. Until that transaction
 * commits, no other connection can see the row: a read through the system connection comes back
 * empty. Readers of the tenant table therefore ask {@link #isUnderCreation(UUID)} and, for the one
 * tenant this transaction is creating, read on the transaction's connection instead.
 *
 * <p>The marker is a {@link TransactionSynchronization}: it lives and dies with the transaction
 * that registered it. A new or suspended transaction has its own synchronization list and never
 * sees it, and it stops answering {@code true} just before the commit, so a callback that runs
 * after the commit reads the committed row through the system connection again.
 *
 * <p>Deliberately free of {@code SystemTransactionExecutor}: that class is a whitelisted BYPASSRLS
 * chokepoint (ConstitutionArchTest rule 14.1), and this one only touches the primary connection.
 */
@Component
public class TenantUnderCreation {

  private final JdbcTemplate primaryJdbc;

  /**
   * @param dataSource the primary ({@code fabric_app}) data source: {@code PrimaryDataSourceConfig}
   *     binds a JDBC template on exactly this one to the JPA transaction's connection. A template
   *     on the system data source would not participate.
   */
  public TenantUnderCreation(@Qualifier("dataSource") DataSource dataSource) {
    this.primaryJdbc = new JdbcTemplate(dataSource);
  }

  /**
   * JDBC access on the current transaction's connection, bound to whichever tenant the session is
   * bound to (row-level security applies). Only meaningful inside a primary transaction.
   */
  public JdbcTemplate primaryJdbc() {
    return primaryJdbc;
  }

  /**
   * Records that the current transaction created {@code tenantId}. Without an active transaction
   * synchronization there is nothing to scope the marker to, so nothing is recorded (unit tests
   * call the creator without a transaction).
   */
  public void register(UUID tenantId) {
    if (tenantId == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    for (TransactionSynchronization synchronization :
        TransactionSynchronizationManager.getSynchronizations()) {
      if (synchronization instanceof Marker marker) {
        marker.tenantIds.add(tenantId);
        return;
      }
    }
    Marker marker = new Marker();
    marker.tenantIds.add(tenantId);
    TransactionSynchronizationManager.registerSynchronization(marker);
  }

  /** True while the current transaction is creating {@code tenantId} and has not begun to end. */
  public boolean isUnderCreation(UUID tenantId) {
    if (tenantId == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
      return false;
    }
    for (TransactionSynchronization synchronization :
        TransactionSynchronizationManager.getSynchronizations()) {
      if (synchronization instanceof Marker marker && marker.tenantIds.contains(tenantId)) {
        return true;
      }
    }
    return false;
  }

  private static final class Marker implements TransactionSynchronization {

    private final Set<UUID> tenantIds = new HashSet<>();

    /** Runs before the commit (and before a rollback): from here on the row is not "ours". */
    @Override
    public void beforeCompletion() {
      tenantIds.clear();
    }
  }
}
