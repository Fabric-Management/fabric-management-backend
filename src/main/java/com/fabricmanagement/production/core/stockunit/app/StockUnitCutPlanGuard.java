package com.fabricmanagement.production.core.stockunit.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitCutPlan;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitCutPlanState;
import com.fabricmanagement.production.core.stockunit.domain.exception.StockUnitCutPlanException;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitCutPlanRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Stock release must not undo physical work. It may release the unaffected whole rolls. */
@Component
@RequiredArgsConstructor
public class StockUnitCutPlanGuard
    implements com.fabricmanagement.production.core.stockunit.api.StockUnitCutPlanLifecyclePort {
  private final StockUnitCutPlanRepository plans;
  private final com.fabricmanagement.production.core.stockunit.infra.repository
          .StockUnitCutSessionRepository
      cutSessions;
  private final com.fabricmanagement.production.core.stockunit.infra.repository
          .StockUnitSoftHoldRepository
      holds;
  private final com.fabricmanagement.production.core.batch.infra.repository
          .BatchLotQuantityIntentRepository
      intents;
  private final com.fabricmanagement.production.core.stockunit.api.StockUnitOwnershipPort ownership;
  private final com.fabricmanagement.platform.realtime.app.LiveEditSessionService sessions;
  private final java.time.Clock clock;

  public List<StockUnitCutPlan> active(UUID line) {
    return plans
        .findByTenantIdAndTargetLineIdOrderByCreatedAtDescIdDesc(
            TenantContext.requireTenantId(), line)
        .stream()
        .filter(
            plan ->
                plan.getState() != StockUnitCutPlanState.CANCELLED
                    && plan.getState() != StockUnitCutPlanState.COMPLETED)
        .toList();
  }

  public java.util.Optional<StockUnitCutPlan> latest(UUID line) {
    return plans
        .findByTenantIdAndTargetLineIdOrderByCreatedAtDescIdDesc(
            TenantContext.requireTenantId(), line)
        .stream()
        .filter(plan -> plan.getState() != StockUnitCutPlanState.CANCELLED)
        .findFirst();
  }

  public Set<UUID> physicallyCutSources(UUID line) {
    return active(line).stream()
        .filter(plan -> plan.getRecordedAt() != null)
        .map(StockUnitCutPlan::getCutSourceId)
        .collect(Collectors.toSet());
  }

  public void beforeReplace(UUID line) {
    if (!physicallyCutSources(line).isEmpty())
      throw StockUnitCutPlanException.conflict(
          "PHYSICAL_DISPOSITION_REQUIRED",
          "Verify and dispose of the recorded cut before replacing its source");
    commercialChange(line);
  }

  public void commercialChange(UUID line) {
    var current = active(line);
    if (current.isEmpty()) return;
    current.stream()
        .map(StockUnitCutPlan::getProductId)
        .distinct()
        .sorted()
        .forEach(ownership::lockProduct);
    var physical = physicallyCutSources(line);
    current.forEach(
        plan -> {
          plan.cancel("Commercial stock requirements changed; physical work remains recorded");
          plans.save(plan);
        });
    holds.findByTenantIdAndOwnerLineId(TenantContext.requireTenantId(), line).stream()
        .filter(row -> !physical.contains(row.getStockUnitId()))
        .forEach(row -> row.release(clock.instant()));
    intents
        .findByTenantIdAndOwnerLineId(TenantContext.requireTenantId(), line)
        .forEach(row -> row.release(clock.instant()));
    plans.flush();
  }

  @Override
  public void commercialLineChanged(UUID line) {
    endSourceSessions(line, null);
    commercialChange(line);
  }

  public void endSourceSessions(UUID line, UUID exceptSession) {
    cutSessions
        .findByTenantIdAndTargetLineIdAndEndedFalse(TenantContext.requireTenantId(), line)
        .stream()
        .filter(row -> !java.util.Objects.equals(row.getSessionId(), exceptSession))
        .forEach(
            row -> {
              ownership.lockProduct(row.getProductId());
              sessions.close(
                  com.fabricmanagement.production.core.stockunit.api.StockUnitLeaseKeys.resource(
                      row.getProductId()),
                  row.getSessionId(),
                  row.getActorId());
              row.end();
              cutSessions.save(row);
            });
  }

  @Override
  public void commercialDocumentChanged(String type, UUID document) {
    var rows =
        plans.findByTenantIdAndTargetTypeAndTargetId(
            TenantContext.requireTenantId(), type, document);
    var selections =
        cutSessions.findByTenantIdAndTargetTypeAndTargetIdAndEndedFalse(
            TenantContext.requireTenantId(), type, document);
    java.util.stream.Stream.concat(
            rows.stream().map(StockUnitCutPlan::getTargetLineId),
            selections.stream()
                .map(
                    com.fabricmanagement.production.core.stockunit.domain.StockUnitCutSession
                        ::getTargetLineId))
        .distinct()
        .sorted()
        .forEach(this::commercialLineChanged);
  }

  @Override
  public void reconcileDocument(
      String type, UUID document, java.util.Map<UUID, String> fingerprints) {
    plans
        .findByTenantIdAndTargetTypeAndTargetId(TenantContext.requireTenantId(), type, document)
        .stream()
        .filter(
            plan ->
                plan.getState() != StockUnitCutPlanState.COMPLETED
                    && plan.getState() != StockUnitCutPlanState.CANCELLED)
        .filter(
            plan ->
                !java.util.Objects.equals(
                    plan.getTargetFingerprint(), fingerprints.get(plan.getTargetLineId())))
        .map(StockUnitCutPlan::getTargetLineId)
        .distinct()
        .sorted()
        .forEach(this::commercialLineChanged);
    cutSessions
        .findByTenantIdAndTargetTypeAndTargetIdAndEndedFalse(
            TenantContext.requireTenantId(), type, document)
        .stream()
        .filter(
            row ->
                !java.util.Objects.equals(
                    row.getTargetFingerprint(), fingerprints.get(row.getTargetLineId())))
        .map(
            com.fabricmanagement.production.core.stockunit.domain.StockUnitCutSession
                ::getTargetLineId)
        .distinct()
        .sorted()
        .forEach(this::commercialLineChanged);
  }
}
