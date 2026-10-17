package com.fabricmanagement.platform.tenant.infra.repository;

import com.fabricmanagement.platform.tenant.domain.BrandAssetKind;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Serialises writes even when the slot does not yet have a row. Uses the application/RLS
 * connection.
 */
@Repository
public class TenantBrandAssetLockRepository {
  private final JdbcTemplate jdbc;

  public TenantBrandAssetLockRepository(@Qualifier("dataSource") DataSource dataSource) {
    jdbc = new JdbcTemplate(dataSource);
  }

  public void lock(UUID tenantId, BrandAssetKind kind) {
    jdbc.query(
        "select pg_advisory_xact_lock(hashtextextended(?, 0))",
        (rs, row) -> Boolean.TRUE,
        tenantId + ":TENANT_BRAND:" + kind);
  }
}
