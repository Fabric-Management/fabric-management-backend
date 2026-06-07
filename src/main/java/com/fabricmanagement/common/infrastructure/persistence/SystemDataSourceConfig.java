package com.fabricmanagement.common.infrastructure.persistence;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Secondary DataSource for system-level operations that require BYPASSRLS.
 *
 * <p>Uses {@code fabric_system} role (BYPASSRLS, NOCREATEDB) — separate from {@code fabric_owner}
 * (migration-only) and {@code fabric_app} (tenant-scoped runtime).
 *
 * <h2>Use Cases:</h2>
 *
 * <ul>
 *   <li>Cross-tenant queries (schedulers iterating all tenants)
 *   <li>Auth login flow (tenant resolution before context is set)
 *   <li>Tenant creation (WITH CHECK blocks cross-tenant INSERT on tenant table)
 *   <li>Platform admin aggregation queries
 * </ul>
 *
 * <p><b>CR-4 Fix:</b> Removed Flyway owner credential fallback. Missing {@code
 * application.system-datasource.username} now causes startup failure instead of silently using the
 * DDL-capable {@code fabric_owner} role at runtime.
 *
 * @see SystemTransactionExecutor
 */
@Configuration
@Slf4j
public class SystemDataSourceConfig {

  @Bean("systemDataSource")
  @ConditionalOnProperty(
      name = "application.system-datasource.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public DataSource systemDataSource(
      @Value("${spring.flyway.url:${spring.datasource.url}}") String url,
      @Value("${application.system-datasource.username:}") String user,
      @Value("${application.system-datasource.password:}") String password,
      @Value("${spring.flyway.user:}") String flywayUser) {

    // CR-4: Fail-fast guard — prevent accidental use of DDL-capable Flyway owner at runtime
    if (user == null || user.isBlank()) {
      throw new IllegalStateException(
          "application.system-datasource.username is NOT configured. "
              + "The system DataSource requires the fabric_system role (BYPASSRLS). "
              + "Set POSTGRES_SYSTEM_USER env var or application.system-datasource.username property.");
    }
    if (user.equals(flywayUser)) {
      log.warn(
          "⚠️ system-datasource.username ({}) matches the Flyway owner role. "
              + "In production, use a dedicated fabric_system role without DDL privileges.",
          user);
    }

    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(url);
    ds.setUsername(user);
    ds.setPassword(password);
    ds.setMaximumPoolSize(3);
    ds.setMinimumIdle(1);
    ds.setPoolName("system-bypass-pool");
    ds.setConnectionTimeout(10_000);
    ds.setIdleTimeout(300_000);
    ds.setMaxLifetime(900_000);
    return ds;
  }

  @Bean("systemJdbcTemplate")
  public JdbcTemplate systemJdbcTemplate(
      @Qualifier("systemDataSource") DataSource systemDataSource) {
    return new JdbcTemplate(systemDataSource);
  }

  @Bean("systemTransactionManager")
  public PlatformTransactionManager systemTransactionManager(
      @Qualifier("systemDataSource") DataSource systemDataSource) {
    return new DataSourceTransactionManager(systemDataSource);
  }
}
