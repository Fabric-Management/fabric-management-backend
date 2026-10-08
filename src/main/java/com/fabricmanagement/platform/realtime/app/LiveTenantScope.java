package com.fabricmanagement.platform.realtime.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.realtime.domain.LiveActor;
import java.util.function.Supplier;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs one revision read as the connection's verified actor (CEDIT-05 §4.5–4.6). The tenant and
 * user are bound before the transaction begins, because the connection provider binds the database
 * session's tenant when the transaction's session opens; RLS then applies with the application's
 * role. Each read is a new, short, read-only transaction with its own persistence context. Whatever
 * context the thread had before is restored afterwards, also when the read fails, so a shared
 * worker never carries one tenant's context into another tenant's job.
 */
@Component
public class LiveTenantScope {

  private final TransactionTemplate readOnly;

  public LiveTenantScope(
      PlatformTransactionManager transactionManager, LiveStreamProperties properties) {
    this.readOnly = new TransactionTemplate(transactionManager);
    this.readOnly.setReadOnly(true);
    this.readOnly.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.readOnly.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.readOnly.setTimeout(
        (int) Math.max(1, (properties.getReadTimeout().toMillis() + 999) / 1000));
  }

  public <T> T read(LiveActor actor, Supplier<T> work) {
    TenantContext.TenantSnapshot previousTenant = TenantContext.capture();
    SecurityContext previousSecurity = SecurityContextHolder.getContext();
    boolean hadAuthentication = previousSecurity.getAuthentication() != null;
    try {
      TenantContext.clear();
      TenantContext.setCurrentTenantId(actor.tenantId());
      TenantContext.setCurrentUserId(actor.userId());
      // Nothing in the read may act on an earlier request's principal; the actor is explicit.
      SecurityContextHolder.clearContext();
      return readOnly.execute(status -> work.get());
    } finally {
      if (hadAuthentication) {
        SecurityContextHolder.setContext(previousSecurity);
      } else {
        SecurityContextHolder.clearContext();
      }
      TenantContext.restore(previousTenant);
    }
  }
}
