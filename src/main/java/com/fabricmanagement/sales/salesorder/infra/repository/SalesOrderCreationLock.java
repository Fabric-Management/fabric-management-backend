package com.fabricmanagement.sales.salesorder.infra.repository;

import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Serialises order creation with one client key (ADR-0014 D10): a transaction-scoped advisory lock
 * per (tenant, key), released when the creating transaction ends.
 */
@Repository
public class SalesOrderCreationLock {

  private final JdbcTemplate jdbc;

  public SalesOrderCreationLock(DataSource dataSource) {
    this.jdbc = new JdbcTemplate(dataSource);
  }

  public void lock(UUID tenantId, UUID key) {
    jdbc.query(
        "select pg_advisory_xact_lock(hashtextextended(?, 0))",
        (rs, row) -> true,
        tenantId + ":" + key + ":SALES_ORDER_CREATE");
  }
}
