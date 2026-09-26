package com.fabricmanagement.flowboard.generator.app.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueFinding;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueReconciliationResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** TASK-TEMPLATE-TENANCY-1 §6 targets and §7 failure semantics of the startup backfill. */
class TaskTemplateCatalogueBackfillRunnerTest {

  private final TenantQueryPort tenants = mock(TenantQueryPort.class);
  private final CatalogueSourceReader reader = mock(CatalogueSourceReader.class);
  private final TenantCatalogueWriter writer = mock(TenantCatalogueWriter.class);
  private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
  private TaskTemplateCatalogueBackfillRunner runner;

  private final UUID regular = UUID.randomUUID();
  private final UUID playground = UUID.randomUUID();
  private final UUID templateTyped = UUID.randomUUID();
  private final UUID nexus = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    runner = new TaskTemplateCatalogueBackfillRunner(tenants, reader, writer, transactions);
    when(tenants.findAllLiveTenants())
        .thenReturn(
            List.of(
                ref(TenantContext.SYSTEM_TENANT_ID, "REGULAR"), // the sentinel is typed REGULAR
                ref(TenantContext.TEMPLATE_TENANT_ID, "TEMPLATE"),
                ref(regular, "REGULAR"),
                ref(playground, "PLAYGROUND"),
                ref(templateTyped, "TEMPLATE"),
                ref(nexus, "TEMPLATE")));
    when(tenants.findPlaygroundSourceTenant()).thenReturn(Optional.of(ref(nexus, "TEMPLATE")));
    when(reader.readCompleteCatalogue(TenantContext.TEMPLATE_TENANT_ID)).thenReturn(List.of());
  }

  @Test
  void targetsAreBusinessTenantsPlusPlaygroundSourceNeverSystemOrGolden() {
    assertThat(runner.targets()).containsExactly(regular, playground, nexus);
  }

  @Test
  void findingsDoNotFailStartup() {
    when(writer.reconcile(any(), anyList()))
        .thenReturn(
            new CatalogueReconciliationResult(
                0,
                0,
                5,
                List.of(
                    new CatalogueFinding(
                        "QUOTE_SEND_REQUESTED__APPROVAL",
                        CatalogueFinding.MULTIPLE_CANDIDATES,
                        List.of(UUID.randomUUID())))));

    runner.run();

    verify(writer).reconcile(eq(regular), anyList());
    verify(writer).reconcile(eq(playground), anyList());
    verify(writer).reconcile(eq(nexus), anyList());
  }

  @Test
  void technicalFailureFailsStartupAfterEarlierTenantsWereWritten() {
    CatalogueReconciliationResult ok = new CatalogueReconciliationResult(5, 0, 0, List.of());
    when(writer.reconcile(eq(regular), anyList())).thenReturn(ok);
    when(writer.reconcile(eq(playground), anyList()))
        .thenThrow(new DataIntegrityViolationException("injected"));

    assertThatThrownBy(runner::run).isInstanceOf(DataIntegrityViolationException.class);

    verify(writer).reconcile(eq(regular), anyList());
    verify(writer, never()).reconcile(eq(nexus), anyList());
    verify(transactions).commit(any());
  }

  @Test
  void incompleteGoldenFailsStartupBeforeAnyTenant() {
    when(reader.readCompleteCatalogue(TenantContext.TEMPLATE_TENANT_ID))
        .thenThrow(new IllegalStateException("golden lacks a key"));

    assertThatThrownBy(runner::run).isInstanceOf(IllegalStateException.class);

    verify(writer, never()).reconcile(any(), anyList());
  }

  @Test
  void eachTenantIsReconciledWithItsOwnTenantBound() {
    when(writer.reconcile(any(), anyList()))
        .thenAnswer(
            invocation -> {
              assertThat(TenantContext.getCurrentTenantIdOrNull())
                  .isEqualTo(invocation.getArgument(0));
              return new CatalogueReconciliationResult(0, 0, 5, List.of());
            });

    runner.run();

    assertThat(TenantContext.getCurrentTenantIdOrNull()).isNull();
  }

  private static TenantReference ref(UUID id, String type) {
    return new TenantReference(id, "UID-" + id, "Tenant " + id, type);
  }
}
