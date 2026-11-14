package com.fabricmanagement.platform.tenant.app.adapter;

import com.fabricmanagement.common.infrastructure.tenant.TenantNamePort;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TenantNameAdapter implements TenantNamePort {

  private final JdbcTemplate jdbc;

  public TenantNameAdapter(@Qualifier("dataSource") DataSource dataSource) {
    this.jdbc = new JdbcTemplate(dataSource);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void rename(UUID tenantId, String name) {
    int updated =
        jdbc.update(
            """
            UPDATE common_tenant.common_tenant
            SET name = ?, updated_at = now(), version = version + 1
            WHERE id = ?
            """,
            name,
            tenantId);
    if (updated != 1) {
      throw new PlatformDomainException(
          "Tenant display name could not be updated", "ORG_TENANT_NAME_UPDATE_FAILED", 409);
    }
  }
}
