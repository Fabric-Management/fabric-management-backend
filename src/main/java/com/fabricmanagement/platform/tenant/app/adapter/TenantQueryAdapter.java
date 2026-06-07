package com.fabricmanagement.platform.tenant.app.adapter;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing {@link TenantQueryPort} using {@link SystemTransactionExecutor} to bypass
 * RLS.
 *
 * <p><b>CRITICAL:</b> All methods here execute via {@code fabric_system} (BYPASSRLS) because the
 * tenant table has self-row RLS ({@code id = current_setting('app.current_tenant')}). Without
 * bypass, cross-tenant queries (scheduler iteration, auth login) would see 0 or 1 row.
 *
 * <p>Uses pure JDBC — no JPA/Hibernate involvement — to avoid tenant context contamination.
 */
@Component
@RequiredArgsConstructor
public class TenantQueryAdapter implements TenantQueryPort {

  private final SystemTransactionExecutor systemExecutor;

  private static final String SELECT_ACTIVE =
      "SELECT id, uid, name, type FROM common_tenant.common_tenant WHERE is_active = true ORDER BY created_at DESC";

  private static final String SELECT_BY_ID =
      "SELECT id, uid, name, type FROM common_tenant.common_tenant WHERE id = ? AND is_active = true";

  private static final String SELECT_BY_IDS =
      "SELECT id, uid, name, type FROM common_tenant.common_tenant WHERE id = ANY(?) AND is_active = true";

  @Override
  public List<TenantReference> findAllActiveTenants() {
    return systemExecutor.executeQuery(
        SELECT_ACTIVE,
        (rs, i) ->
            new TenantReference(
                rs.getObject("id", UUID.class),
                rs.getString("uid"),
                rs.getString("name"),
                rs.getString("type")));
  }

  @Override
  public List<TenantReference> findAllByIds(Collection<UUID> tenantIds) {
    if (tenantIds == null || tenantIds.isEmpty()) {
      return Collections.emptyList();
    }
    UUID[] idArray = tenantIds.toArray(new UUID[0]);
    return systemExecutor.executeQuery(
        SELECT_BY_IDS,
        (rs, i) ->
            new TenantReference(
                rs.getObject("id", UUID.class),
                rs.getString("uid"),
                rs.getString("name"),
                rs.getString("type")),
        (Object) idArray);
  }

  @Override
  public Optional<TenantReference> findById(UUID tenantId) {
    TenantReference result =
        systemExecutor.executeQueryForObject(
            SELECT_BY_ID,
            (rs, i) ->
                new TenantReference(
                    rs.getObject("id", UUID.class),
                    rs.getString("uid"),
                    rs.getString("name"),
                    rs.getString("type")),
            tenantId);
    return Optional.ofNullable(result);
  }
}
