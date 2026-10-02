package com.fabricmanagement.production.playground.app.adapter;

import com.fabricmanagement.common.infrastructure.bootstrap.PlaygroundSeedScopePort;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.playground.infra.repository.PlaygroundFixtureRunRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A playground fixture run exists only for the two eligible initial-provisioning origins (the gate
 * in {@link PlaygroundFixtureProvisioningAdapter}) and is purged with the tenant's transactional
 * data on reset, so its presence is the trusted answer to "was this tenant provisioned as a
 * playground?" without re-deriving eligibility from type or demo flags.
 *
 * <p>Deliberately not {@code @Transactional}: {@code TenantConnectionProvider} binds {@code
 * app.current_tenant} when the transaction acquires its connection, reading {@link TenantContext}
 * at that moment. A transaction opened before the tenant is set runs under the wrong (or no)
 * tenant, and row-level security then hides the run row — the check answered "no playground" for
 * every real playground. So the tenant is set first and the transaction opened inside it. It is a
 * new transaction because the demo chain asks this from an afterCommit hook.
 */
@Component
public class PlaygroundSeedScopeAdapter implements PlaygroundSeedScopePort {

  private final PlaygroundFixtureRunRepository runRepository;
  private final TransactionTemplate readInNewTransaction;

  public PlaygroundSeedScopeAdapter(
      PlaygroundFixtureRunRepository runRepository, PlatformTransactionManager transactionManager) {
    this.runRepository = runRepository;
    this.readInNewTransaction = new TransactionTemplate(transactionManager);
    this.readInNewTransaction.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.readInNewTransaction.setReadOnly(true);
  }

  @Override
  public boolean isInitialPlaygroundProvisioning(UUID tenantId) {
    if (tenantId == null) {
      return false;
    }
    return TenantContext.executeInTenantContext(
        tenantId,
        () ->
            Boolean.TRUE.equals(
                readInNewTransaction.execute(
                    status -> runRepository.findByTenantId(tenantId).isPresent())));
  }
}
