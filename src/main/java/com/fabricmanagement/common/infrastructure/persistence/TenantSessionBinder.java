package com.fabricmanagement.common.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Re-binds the PostgreSQL session variable {@code app.current_tenant} on the CURRENT transaction's
 * connection.
 *
 * <p>{@link TenantConnectionProvider} binds the session variable only when Hibernate first acquires
 * the connection for a session (via the {@code TenantIdentifierResolver} reading {@link
 * TenantContext} at that moment). With aggressive release disabled, that connection is held for the
 * whole transaction and the binding is never refreshed. So if the tenant changes <em>mid</em>
 * transaction — most notably self-service onboarding, which creates the tenant inside an already
 * open transaction (bound to {@code SYSTEM_TENANT_ID}) and then switches {@link TenantContext} to
 * the new tenant — the DB session variable stays on the old tenant. RLS then evaluates {@code WITH
 * CHECK (tenant_id = current_setting('app.current_tenant'))} against the old tenant and rejects
 * inserts into tenant-scoped tables (e.g. {@code common_organization}).
 *
 * <p>Calling {@link #bindToCurrentSession(UUID)} right after such a switch runs {@code set_config}
 * on the same transaction-bound connection, keeping the Java {@link TenantContext} and the DB
 * session variable in sync for the remainder of the transaction. The connection is still cleared
 * back to {@code NULL} on release by {@link TenantConnectionProvider}.
 */
@Component
@Slf4j
public class TenantSessionBinder {

  @PersistenceContext private EntityManager entityManager;

  /**
   * Sets {@code app.current_tenant} on the current transaction's connection to {@code tenantId}.
   * Must be called within an active transaction so it runs on the same connection as the subsequent
   * tenant-scoped writes.
   */
  public void bindToCurrentSession(UUID tenantId) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenant', ?1, false)")
        .setParameter(1, tenantId.toString())
        .getSingleResult();
    log.debug("Re-bound DB session to tenant {} mid-transaction", tenantId);
  }
}
