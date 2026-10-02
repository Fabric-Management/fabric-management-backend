package com.fabricmanagement.flowboard.generator.app.catalogue;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueReconciliationResult;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueSourceRow;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Startup backfill of the task-template catalogue into every target tenant (TASK-TEMPLATE-TENANCY-1
 * §6–§7).
 *
 * <p>Targets: live tenants of type REGULAR or PLAYGROUND, plus the playground clone source, minus
 * SYSTEM_TENANT_ID and golden-template (excluded by id; the SYSTEM row is typed REGULAR). Each
 * tenant is reconciled in its own primary transaction as {@code fabric_app}, with the tenant bound
 * before the transaction starts.
 *
 * <p>Findings (R3, {@code UNKEYED_SIBLING}) are logged and startup continues. A technical failure
 * is logged with the tenant id and rethrown, which fails startup: an application that cannot
 * distribute the catalogue must not serve traffic and keep reproducing invisible templates. Tenants
 * committed before the failure stay valid; the backfill is idempotent.
 */
@Component
@Slf4j
public class TaskTemplateCatalogueBackfillRunner {

  static final String REGULAR = "REGULAR";
  static final String PLAYGROUND = "PLAYGROUND";

  private final TenantQueryPort tenantQueryPort;
  private final CatalogueSourceReader sourceReader;
  private final TenantCatalogueWriter writer;
  private final TransactionTemplate transactionTemplate;

  public TaskTemplateCatalogueBackfillRunner(
      TenantQueryPort tenantQueryPort,
      CatalogueSourceReader sourceReader,
      TenantCatalogueWriter writer,
      PlatformTransactionManager transactionManager) {
    this.tenantQueryPort = tenantQueryPort;
    this.sourceReader = sourceReader;
    this.writer = writer;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(250) // after DevSeedDataRunner (100) creates tenants and the product backfills (210-240)
  public void run() {
    List<CatalogueSourceRow> golden;
    try {
      golden = sourceReader.readCompleteCatalogue(TenantContext.TEMPLATE_TENANT_ID);
    } catch (RuntimeException e) {
      log.error("CRITICAL: task-template catalogue in golden-template is incomplete.", e);
      throw e;
    }

    for (UUID tenantId : targets()) {
      CatalogueReconciliationResult result;
      try {
        result =
            TenantContext.executeInTenantContext(
                tenantId,
                () -> transactionTemplate.execute(status -> writer.reconcile(tenantId, golden)));
      } catch (RuntimeException e) {
        log.error(
            "CRITICAL: task-template catalogue backfill failed for tenant={}; failing startup.",
            tenantId,
            e);
        throw e;
      }
      log.info(
          "Task-template catalogue backfill: tenant={} inserted={} adopted={} unchanged={}"
              + " findings={}",
          tenantId,
          result.inserted(),
          result.adopted(),
          result.unchanged(),
          result.findings().size());
    }
  }

  /** Ticket §6 target set, from the public tenant-query contract only. */
  Set<UUID> targets() {
    Set<UUID> targets = new LinkedHashSet<>();
    for (TenantReference tenant : tenantQueryPort.findAllLiveTenants()) {
      if (REGULAR.equals(tenant.type()) || PLAYGROUND.equals(tenant.type())) {
        targets.add(tenant.id());
      }
    }
    tenantQueryPort.findPlaygroundSourceTenant().ifPresent(source -> targets.add(source.id()));
    targets.remove(TenantContext.SYSTEM_TENANT_ID);
    targets.remove(TenantContext.TEMPLATE_TENANT_ID);
    return targets;
  }
}
