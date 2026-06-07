package com.fabricmanagement.common.infrastructure.persistence;

import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executes database operations using the system DataSource ({@code fabric_system} role) which has
 * {@code BYPASSRLS} capability.
 *
 * <p><b>CRITICAL:</b> This executor bypasses Row-Level Security. Use ONLY for legitimate
 * cross-tenant operations:
 *
 * <ul>
 *   <li>Tenant resolution (login, slug lookup)
 *   <li>Cross-tenant iteration (schedulers calling {@code findAllActiveTenants()})
 *   <li>Tenant creation (INSERT into tenant table)
 *   <li>Platform admin queries
 * </ul>
 *
 * <p>Does NOT interact with {@link TenantContext}. All operations use pure JDBC, not JPA, to avoid
 * Hibernate tenant context contamination.
 *
 * @see SystemDataSourceConfig
 */
@Component
@Slf4j
public class SystemTransactionExecutor {

  private final JdbcTemplate systemJdbcTemplate;
  private final TransactionTemplate systemTransactionTemplate;

  public SystemTransactionExecutor(
      @Qualifier("systemJdbcTemplate") JdbcTemplate systemJdbcTemplate,
      @Qualifier("systemTransactionManager") PlatformTransactionManager systemTxManager) {
    this.systemJdbcTemplate = systemJdbcTemplate;
    this.systemTransactionTemplate = new TransactionTemplate(systemTxManager);
  }

  /**
   * Executes a read-only query bypassing RLS.
   *
   * @param sql the SQL query
   * @param mapper row mapper
   * @param args query arguments
   * @param <T> result type
   * @return list of mapped results
   */
  public <T> List<T> executeQuery(String sql, RowMapper<T> mapper, Object... args) {
    log.trace("System query (BYPASSRLS): {}", sql);
    return systemJdbcTemplate.query(sql, mapper, args);
  }

  /**
   * Executes a single-result query bypassing RLS.
   *
   * @param sql the SQL query
   * @param mapper row mapper
   * @param args query arguments
   * @param <T> result type
   * @return mapped result or null
   */
  public <T> T executeQueryForObject(String sql, RowMapper<T> mapper, Object... args) {
    log.trace("System query for object (BYPASSRLS): {}", sql);
    List<T> results = systemJdbcTemplate.query(sql, mapper, args);
    return results.isEmpty() ? null : results.getFirst();
  }

  /**
   * Executes work within a transaction bypassing RLS.
   *
   * @param work function receiving JdbcTemplate, returning result
   * @param <T> result type
   * @return the result from the work function
   */
  public <T> T executeInTransaction(Function<JdbcTemplate, T> work) {
    log.trace("System transaction (BYPASSRLS) starting");
    return systemTransactionTemplate.execute(status -> work.apply(systemJdbcTemplate));
  }

  /**
   * Executes a DML statement (INSERT/UPDATE/DELETE) bypassing RLS.
   *
   * @param sql the SQL statement
   * @param args statement arguments
   * @return number of rows affected
   */
  public int executeUpdate(String sql, Object... args) {
    log.trace("System update (BYPASSRLS): {}", sql);
    return systemJdbcTemplate.update(sql, args);
  }
}
