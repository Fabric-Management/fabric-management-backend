package com.fabricmanagement.flowboard.generator.app.catalogue;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.generator.domain.TaskTemplate;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueCandidate;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueDecision;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueFinding;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueReconciler;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueReconciliationResult;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueSourceRow;
import com.fabricmanagement.flowboard.generator.domain.catalogue.TaskTemplateCatalogue;
import com.fabricmanagement.flowboard.generator.infra.repository.TaskTemplateRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles the catalogue into the tenant the current transaction is bound to (ticket §5), through
 * JPA on the primary ({@code fabric_app}) connection.
 *
 * <p>{@code MANDATORY}: it never opens a transaction of its own. Onboarding calls it inside the
 * onboarding transaction; the backfill opens one transaction per tenant around it. A rollback of
 * the caller therefore removes every row written here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantCatalogueWriter {

  private final TaskTemplateRepository repository;

  @Transactional(propagation = Propagation.MANDATORY)
  public CatalogueReconciliationResult reconcile(
      UUID targetTenantId, List<CatalogueSourceRow> source) {
    UUID bound = TenantContext.requireTenantId();
    if (!bound.equals(targetTenantId)) {
      throw new IllegalStateException(
          "Catalogue target " + targetTenantId + " differs from the bound tenant " + bound);
    }
    Map<String, CatalogueSourceRow> sourceByKey = new HashMap<>();
    source.forEach(row -> sourceByKey.put(row.catalogKey(), row));

    int inserted = 0;
    int adopted = 0;
    int unchanged = 0;
    List<CatalogueFinding> findings = new ArrayList<>();
    for (TaskTemplateCatalogue entry : TaskTemplateCatalogue.values()) {
      CatalogueSourceRow sourceRow = sourceByKey.get(entry.key());
      if (sourceRow == null) {
        throw new IllegalStateException("Catalogue source lacks " + entry.key());
      }
      List<TaskTemplate> rows = rowsFor(targetTenantId, entry);
      CatalogueDecision decision =
          CatalogueReconciler.decide(
              entry, rows.stream().map(TenantCatalogueWriter::candidate).toList());
      switch (decision.kind()) {
        case R1_INSERT -> {
          repository.save(TaskTemplate.catalogueCopy(sourceRow));
          inserted++;
        }
        case R2_ADOPT -> {
          TaskTemplate row =
              rows.stream()
                  .filter(candidate -> candidate.getId().equals(decision.adoptId()))
                  .findFirst()
                  .orElseThrow();
          row.adoptCatalogKey(entry.key());
          repository.save(row);
          adopted++;
        }
        case R0_KEYED, R3_NO_WRITE -> unchanged++;
      }
      findings.addAll(decision.findings());
    }
    // Surface constraint violations here, attributed to this tenant, not at commit time.
    repository.flush();
    findings.forEach(
        finding ->
            log.warn(
                "Task-template catalogue finding: tenant={} key={} reason={} candidates={}",
                targetTenantId,
                finding.catalogKey(),
                finding.reason(),
                finding.candidateIds()));
    return new CatalogueReconciliationResult(inserted, adopted, unchanged, findings);
  }

  private List<TaskTemplate> rowsFor(UUID tenantId, TaskTemplateCatalogue entry) {
    List<TaskTemplate> rows =
        new ArrayList<>(
            repository.findAllByTenantIdAndEventTypeAndTaskType(
                tenantId, entry.eventType(), entry.taskType()));
    // A tenant may have edited its keyed copy's event or task type; the key still identifies it.
    repository
        .findByTenantIdAndCatalogKey(tenantId, entry.key())
        .filter(keyed -> rows.stream().noneMatch(row -> row.getId().equals(keyed.getId())))
        .ifPresent(rows::add);
    return rows;
  }

  static CatalogueCandidate candidate(TaskTemplate row) {
    return new CatalogueCandidate(
        row.getId(),
        row.getCatalogKey(),
        row.getEventType(),
        row.getTaskType().name(),
        row.getName(),
        row.getDescription(),
        row.getUid(),
        row.getCreatedBy(),
        row.getUpdatedBy(),
        row.getVersion() == null ? 0L : row.getVersion(),
        row.getDeletedAt(),
        TaskTemplateCatalogue.fingerprint(
            row.getTitleTemplate(),
            row.getTaskType().name(),
            row.getModuleType() == null ? null : row.getModuleType().name(),
            row.getDefaultPriority().name(),
            row.getDefaultAssigneeRole().name(),
            row.getEstimatedHours(),
            row.getAutoLabels(),
            row.getChecklistTemplate()));
  }
}
