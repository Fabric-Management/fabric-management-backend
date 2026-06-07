package com.fabricmanagement.common.infrastructure.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Multi-Tenant Connection Provider (MTCP) that binds the current tenant ID to the PostgreSQL
 * session.
 *
 * <p>Uses {@code set_config('app.current_tenant', ?, false)} at the start of the connection usage,
 * and clears it to NULL when the connection is released back to the pool. The {@code false} flag
 * means the setting applies to the session, not just the current transaction, which ensures that
 * non-transactional reads (autocommit) are also isolated.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantConnectionProvider
    implements MultiTenantConnectionProvider<String>, HibernatePropertiesCustomizer {

  private final DataSource dataSource;

  @Override
  public Connection getAnyConnection() throws SQLException {
    // Used by Hibernate at startup (e.g., schema validation)
    return dataSource.getConnection();
  }

  @Override
  public void releaseAnyConnection(Connection connection) throws SQLException {
    connection.close();
  }

  @Override
  public Connection getConnection(String tenantIdentifier) throws SQLException {
    Connection connection = dataSource.getConnection();
    try (PreparedStatement stmt =
        connection.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
      stmt.setString(1, tenantIdentifier);
      stmt.execute();
      log.trace("Bound connection to tenant: {}", tenantIdentifier);
    } catch (SQLException e) {
      connection.close(); // Prevent connection leak on failure
      throw new SQLException("Could not bind tenant to connection", e);
    }
    return connection;
  }

  @Override
  public void releaseConnection(String tenantIdentifier, Connection connection)
      throws SQLException {
    try (PreparedStatement stmt =
        connection.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
      stmt.setString(1, null);
      stmt.execute();
      log.trace("Released connection from tenant: {}", tenantIdentifier);
    } catch (SQLException e) {
      log.error(
          "Could not release tenant binding on connection, aborting connection to prevent leak", e);
      try {
        connection.abort(Runnable::run);
      } catch (SQLException ex) {
        log.warn("Failed to abort connection", ex);
      }
      // CR-6: Return after abort — do NOT fall through to close() on an aborted connection
      return;
    }
    // Normal path: close the connection after successfully clearing tenant binding
    try {
      connection.close();
    } catch (SQLException closeEx) {
      log.warn("Failed to close connection during release", closeEx);
    }
  }

  @Override
  public boolean supportsAggressiveRelease() {
    // False ensures Hibernate holds onto the connection for the entire session.
    // If true, it might release and re-acquire mid-transaction, losing our session variable.
    return false;
  }

  @Override
  public boolean isUnwrappableAs(Class<?> unwrapType) {
    return false;
  }

  @Override
  public <T> T unwrap(Class<T> unwrapType) {
    throw new UnsupportedOperationException("Unwrap not supported");
  }

  @Override
  public void customize(Map<String, Object> hibernateProperties) {
    hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, this);
  }
}
