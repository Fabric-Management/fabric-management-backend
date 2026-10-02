package com.fabricmanagement.flowboard.generator.app.adapter;

import com.fabricmanagement.flowboard.generator.app.catalogue.CatalogueSourceReader;
import com.fabricmanagement.flowboard.generator.app.catalogue.PlaygroundCatalogueWriter;
import com.fabricmanagement.flowboard.generator.app.catalogue.TenantCatalogueWriter;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueFinding;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueReconciliationResult;
import com.fabricmanagement.platform.tenant.domain.port.CatalogueProvisioningReport;
import com.fabricmanagement.platform.tenant.domain.port.TenantCatalogueProvisioningPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * FlowBoard's implementation of platform's catalogue provisioning port (TASK-TEMPLATE-TENANCY-1
 * §7). FlowBoard owns the task-template catalogue; platform only calls this port where it creates
 * tenants. Both paths join the caller's transaction and throw without it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantCatalogueProvisioningAdapter implements TenantCatalogueProvisioningPort {

  private final CatalogueSourceReader sourceReader;
  private final TenantCatalogueWriter tenantWriter;
  private final PlaygroundCatalogueWriter playgroundWriter;

  @Override
  public CatalogueProvisioningReport provisionFromTemplate(
      UUID sourceTenantId, UUID targetTenantId) {
    CatalogueReconciliationResult result =
        tenantWriter.reconcile(targetTenantId, sourceReader.readCompleteCatalogue(sourceTenantId));
    log.info(
        "Task-template catalogue provisioned: tenant={} source={} inserted={} adopted={}"
            + " unchanged={} findings={}",
        targetTenantId,
        sourceTenantId,
        result.inserted(),
        result.adopted(),
        result.unchanged(),
        result.findings().size());
    return report(result);
  }

  @Override
  public CatalogueProvisioningReport provisionPlaygroundFromSource(
      UUID sourceTenantId, UUID targetTenantId, String targetTenantUid) {
    CatalogueReconciliationResult result =
        playgroundWriter.copyFromSource(sourceTenantId, targetTenantId, targetTenantUid);
    log.info(
        "Task-template catalogue copied to playground: tenant={} source={} inserted={}"
            + " adopted={} unchanged={} findings={}",
        targetTenantId,
        sourceTenantId,
        result.inserted(),
        result.adopted(),
        result.unchanged(),
        result.findings().size());
    return report(result);
  }

  private static CatalogueProvisioningReport report(CatalogueReconciliationResult result) {
    return new CatalogueProvisioningReport(
        result.inserted(),
        result.adopted(),
        result.unchanged(),
        result.findings().stream().map(TenantCatalogueProvisioningAdapter::describe).toList());
  }

  private static String describe(CatalogueFinding finding) {
    return finding.catalogKey() + " " + finding.reason() + " " + finding.candidateIds();
  }
}
