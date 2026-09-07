package com.fabricmanagement.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.cache.ContextCacheUtils;

/** Guards the shared container against connection exhaustion as test contexts accumulate. */
class TestDatabaseConnectionBudgetIT extends AbstractIntegrationTest {

  private static final int DIRECT_CONNECTION_HEADROOM = 10;

  @Autowired
  @Qualifier("dataSource")
  private DataSource primaryDataSource;

  @Autowired
  @Qualifier("systemDataSource")
  private DataSource systemDataSource;

  @Autowired
  @Qualifier("systemJdbcTemplate")
  private JdbcTemplate systemJdbcTemplate;

  @Test
  void cachedPoolsLeaveCapacityForStartupAndDirectJdbcConnections() throws SQLException {
    int connectionsPerContext =
        primaryDataSource.unwrap(HikariDataSource.class).getMaximumPoolSize()
            + systemDataSource.unwrap(HikariDataSource.class).getMaximumPoolSize();
    int cachedContexts = ContextCacheUtils.retrieveMaxCacheSize();
    Integer availableConnections =
        systemJdbcTemplate.queryForObject(
            """
            SELECT current_setting('max_connections')::int
                 - current_setting('superuser_reserved_connections')::int
            """,
            Integer.class);

    // Spring loads the next context before inserting it into the cache and evicting the oldest.
    // Include both pools at full capacity, plus transient Flyway and direct JDBC connections.
    long peakConnectionBudget =
        (cachedContexts + 1L) * connectionsPerContext + DIRECT_CONNECTION_HEADROOM;

    assertThat(availableConnections).isNotNull();
    assertThat(peakConnectionBudget)
        .as(
            "cached contexts (%s) plus one starting context, with %s pooled connections each"
                + " and %s direct connections, must fit PostgreSQL capacity (%s)",
            cachedContexts, connectionsPerContext, DIRECT_CONNECTION_HEADROOM, availableConnections)
        .isLessThanOrEqualTo(availableConnections.longValue());
  }
}
