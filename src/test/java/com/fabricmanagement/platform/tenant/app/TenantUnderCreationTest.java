package com.fabricmanagement.platform.tenant.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

class TenantUnderCreationTest {

  private final TenantUnderCreation tenantUnderCreation =
      new TenantUnderCreation(mock(DataSource.class));

  @AfterEach
  void clearSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void withoutATransactionNothingIsRecorded() {
    UUID tenantId = UUID.randomUUID();

    tenantUnderCreation.register(tenantId);

    assertThat(tenantUnderCreation.isUnderCreation(tenantId)).isFalse();
  }

  @Test
  void aRegisteredTenantIsUnderCreationOnlyInTheTransactionThatRegisteredIt() {
    UUID tenantId = UUID.randomUUID();
    TransactionSynchronizationManager.initSynchronization();

    tenantUnderCreation.register(tenantId);

    assertThat(tenantUnderCreation.isUnderCreation(tenantId)).isTrue();
    assertThat(tenantUnderCreation.isUnderCreation(UUID.randomUUID())).isFalse();
    assertThat(tenantUnderCreation.isUnderCreation(null)).isFalse();

    // A transaction of its own (a suspended one, a REQUIRES_NEW) starts with no synchronizations.
    TransactionSynchronizationManager.clearSynchronization();
    TransactionSynchronizationManager.initSynchronization();
    assertThat(tenantUnderCreation.isUnderCreation(tenantId)).isFalse();
  }

  @Test
  void severalTenantsCanBeUnderCreationInOneTransaction() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    TransactionSynchronizationManager.initSynchronization();

    tenantUnderCreation.register(first);
    tenantUnderCreation.register(second);

    assertThat(tenantUnderCreation.isUnderCreation(first)).isTrue();
    assertThat(tenantUnderCreation.isUnderCreation(second)).isTrue();
  }

  @Test
  void aTenantStopsBeingUnderCreationJustBeforeTheTransactionEnds() {
    UUID tenantId = UUID.randomUUID();
    TransactionSynchronizationManager.initSynchronization();
    tenantUnderCreation.register(tenantId);

    // What the transaction manager does before the commit (or a rollback). After this, a callback
    // that runs after the commit reads the committed row through the system connection again.
    TransactionSynchronizationUtils.triggerBeforeCompletion();

    assertThat(tenantUnderCreation.isUnderCreation(tenantId)).isFalse();
  }
}
