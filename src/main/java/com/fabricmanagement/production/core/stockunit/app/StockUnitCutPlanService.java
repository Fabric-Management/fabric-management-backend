package com.fabricmanagement.production.core.stockunit.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.batch.api.BatchLotQuantityIntentPort;
import com.fabricmanagement.production.core.stockunit.api.*;
import com.fabricmanagement.production.core.stockunit.api.StockUnitCutTargetPort.*;
import com.fabricmanagement.production.core.stockunit.domain.*;
import com.fabricmanagement.production.core.stockunit.domain.exception.StockUnitCutPlanException;
import com.fabricmanagement.production.core.stockunit.dto.StockUnitCutPlanDtos.*;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitCutPlanRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lock order: commercial aggregate, operation receipt, plan, inventory gate, sorted lots. */
@Service
@RequiredArgsConstructor
public class StockUnitCutPlanService implements StockUnitCutPlanPort {
  private final StockUnitCutTargetPort targets;
  private final StockUnitCutPlanRepository plans;
  private final StockUnitCutPlanSources sources;
  private final StockUnitCutPlanGuard guard;
  private final StockUnitCutPlanViews views;
  private final StockUnitCutPlanLedger ledger;
  private final StockUnitCutPlanOperations operations;
  private final StockUnitSoftHoldPort holds;
  private final BatchLotQuantityIntentPort intents;
  private final PieceAllocationPort allocations;
  private final EntityManager entityManager;
  private final Clock clock;
  private final StockUnitCutDraftEvidence draftEvidence;

  @Override
  @Transactional
  public View evaluate(
      Context target, List<UUID> pieces, UUID cutSource, UUID session, UUID actor) {
    return views.view(candidate(target, pieces, cutSource, session, actor), target);
  }

  @Override
  @Transactional
  public View evaluateDraft(DraftInput input, UUID actor) {
    var evidence = sources.capture(input.sources(), input.ownLineId(), input.sessionId());
    var quantity = sources.metres(input.quantity(), input.unit());
    var total =
        evidence.stream()
            .map(StockUnitCutPlan.Source::length)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    var cut = input.cutSourceId();
    if (cut == null && total.compareTo(quantity) > 0)
      cut =
          evidence.stream()
              .filter(source -> source.length().compareTo(total.subtract(quantity)) > 0)
              .reduce((first, last) -> last)
              .map(StockUnitCutPlan.Source::stockUnitId)
              .orElse(null);
    if (cut == null)
      throw StockUnitCutPlanException.invalid(
          "CUT_SOURCE_REQUIRED", "Choose sufficient whole sources for the exact quantity");
    var plan =
        StockUnitCutPlan.prepare(
            "DRAFT_LINE",
            input.formId(),
            input.clientLineId(),
            input.productId(),
            "DRAFT_PREVIEW",
            quantity,
            cut,
            evidence,
            actor,
            clock.instant());
    draftEvidence.remember(input, quantity, evidence, cut, actor);
    var context =
        new Context(
            new Target(TargetType.DRAFT_LINE, input.formId(), input.clientLineId()),
            input.productId(),
            quantity,
            "DRAFT_PREVIEW",
            true,
            true,
            false,
            true,
            false,
            "",
            "",
            0L);
    return views.view(plan, context);
  }

  @Override
  @Transactional
  public View replay(UUID operation, UUID actor, UUID line, String hash) {
    return operations.replay(operation, actor, line, hash);
  }

  @Override
  @Transactional
  public View apply(
      Context target,
      List<UUID> pieces,
      UUID cutSource,
      UUID session,
      UUID operation,
      UUID actor,
      String hash) {
    var prior = operations.replay(operation, actor, target.target().lineId(), hash);
    if (prior != null) return prior;
    var plan = candidate(target, pieces, cutSource, session, actor);
    draftEvidence.assertCurrent(target, plan.getSources(), cutSource, session, actor);
    prior = operations.replay(operation, actor, target.target().lineId(), hash);
    if (prior != null) return prior;
    if (target.executionReleased()
        && !allocations
            .activeForLine(TenantContext.requireTenantId(), target.target().lineId())
            .isEmpty()) {
      allocations.releaseForLine(
          TenantContext.requireTenantId(), target.target().lineId(), actor, "CUT_PLAN_REPLACED");
      entityManager.flush();
      plan = candidate(target, pieces, cutSource, session, actor);
    }
    if (target.target().type() == TargetType.SALES_ORDER) {
      holds.replaceOrderHolds(
          target.target().documentId(),
          target.reference(),
          target.target().lineId(),
          actor,
          session,
          pieces,
          lotTotals(plan).entrySet().stream()
              .map(
                  entry ->
                      new StockUnitSoftHoldPort.OrderLot(entry.getKey(), entry.getValue(), "M"))
              .toList());
    } else {
      holds.replaceQuoteHolds(target.target().lineId(), pieces, session);
      var coverage =
          intents.replaceIntents(
              target.target().documentId(),
              target.reference(),
              target.target().lineId(),
              actor,
              "",
              null,
              lotTotals(plan).entrySet().stream()
                  .map(
                      entry ->
                          new BatchLotQuantityIntentPort.LotIntentRequest(
                              entry.getKey(), entry.getValue(), "M"))
                  .toList());
      if (!coverage.covered())
        throw StockUnitCutPlanException.conflict(
            "SOURCE_CHANGED", "The selected lots no longer cover this plan");
    }
    plan = plans.saveAndFlush(plan);
    if (target.executionReleased()) {
      var outcome =
          allocations.allocate(
              new PieceAllocationPort.AllocationRequest(
                  TenantContext.requireTenantId(),
                  target.target().documentId(),
                  target.target().lineId(),
                  actor,
                  plan.getSources().stream()
                      .map(
                          row ->
                              new PieceAllocationPort.PieceRef(
                                  row.stockUnitId(), row.batchId(), row.version()))
                      .toList(),
                  session));
      if (!outcome.allocated())
        throw StockUnitCutPlanException.conflict(
            "SOURCE_CHANGED", "A source roll could not be allocated");
      holds.consumeAtConfirmation(target.target().lineId(), session);
    }
    targets.changed(target);
    return operations.complete(
        operation, actor, target.target().lineId(), hash, views.view(plan, target));
  }

  private StockUnitCutPlan candidate(
      Context target, List<UUID> pieces, UUID cutSource, UUID session, UUID actor) {
    if (!target.canPrepare())
      throw new org.springframework.security.access.AccessDeniedException(
          "Cut planning requires current write access");
    if (!target.active() || !target.editable())
      throw StockUnitCutPlanException.conflict(
          "TARGET_NOT_EDITABLE", "The commercial target cannot change its source plan");
    guard.active(target.target().lineId()).stream()
        .filter(plan -> plan.getRecordedAt() != null)
        .findAny()
        .ifPresent(
            plan -> {
              throw StockUnitCutPlanException.conflict(
                  "ALREADY_CUT", "Resolve the recorded physical cut before replacing its plan");
            });
    var evidence = sources.capture(pieces, target.target().lineId(), session);
    if (!targets.sourcesCompatible(target.target(), pieces))
      throw StockUnitCutPlanException.conflict(
          "SOURCE_CHANGED",
          "The sources do not meet the product, colour, width, lot or compatibility requirements");
    targets.assertFreeSources(target.target(), pieces, session);
    return StockUnitCutPlan.prepare(
        target.target().type().name(),
        target.target().documentId(),
        target.target().lineId(),
        target.productId(),
        target.fingerprint(),
        target.quantity(),
        cutSource,
        evidence,
        actor,
        clock.instant());
  }

  @Transactional(readOnly = true)
  public List<View> list(Target target, UUID actor) {
    var context = targets.resolve(target, actor, false);
    return plans
        .findByTenantIdAndTargetLineIdOrderByCreatedAtDescIdDesc(
            TenantContext.requireTenantId(), target.lineId())
        .stream()
        .filter(
            plan ->
                plan.getTargetType().equals(target.type().name())
                    && plan.getTargetId().equals(target.documentId()))
        .map(plan -> views.view(plan, context))
        .toList();
  }

  @Transactional(readOnly = true)
  public TargetView targetView(Target target, UUID actor) {
    var context = targets.resolve(target, actor, false);
    var reason =
        !context.canPrepare()
            ? Reason.WRITE_REQUIRED
            : !context.active() || !context.editable() ? Reason.TARGET_NOT_EDITABLE : null;
    return new TargetView(
        target.type(),
        target.documentId(),
        target.lineId(),
        context.productId(),
        context.reference(),
        context.documentVersion(),
        context.quantity(),
        "M",
        StockUnitLeaseKeys.liveEventsPath(context.productId()),
        List.of(new Capability(Action.APPLY, reason == null, reason, context.route(), true)));
  }

  @Transactional(readOnly = true)
  public View get(UUID id, UUID actor) {
    var plan = find(id);
    return views.view(plan, targets.resolve(target(plan), actor, false));
  }

  @Transactional
  public View command(UUID id, Action action, Command request, UUID actor) {
    var metadata = find(id);
    var before = target(metadata);
    var context = targets.resolve(before, actor, true);
    String hash = operations.hash(List.of("COMMAND", id, action, request));
    var prior = operations.replay(request.operationId(), actor, metadata.getTargetLineId(), hash);
    if (prior != null) return prior;
    entityManager.refresh(metadata, LockModeType.PESSIMISTIC_WRITE);
    if (!before.equals(target(metadata)))
      throw StockUnitCutPlanException.conflict(
          "TARGET_CHANGED", "The plan moved to another commercial target; reload it");
    if (!Objects.equals(metadata.getVersion(), request.expectedVersion()))
      throw StockUnitCutPlanException.conflict(
          "STALE_VERSION", "The plan changed; reload it and retain your measurements");
    sources.lock(metadata);
    prior = operations.replay(request.operationId(), actor, metadata.getTargetLineId(), hash);
    if (prior != null) return prior;
    var reason = views.reason(metadata, context, action);
    if (reason != null)
      throw StockUnitCutPlanException.conflict(
          reason.name(), "This plan no longer permits " + action);
    var now = clock.instant();
    switch (action) {
      case APPROVE -> metadata.approve(actor, now);
      case RECORD -> metadata.record(actual(request), actor, now);
      case CORRECT -> metadata.correct(actual(request), actor, now);
      case VERIFY ->
          ledger.verify(
              metadata,
              sources.unit(metadata.getCutSourceId()),
              context.active()
                  && Objects.equals(context.fingerprint(), metadata.getTargetFingerprint())
                  && Objects.equals(
                      sources.unit(metadata.getCutSourceId()).getQualityGradeId(),
                      metadata.cutSource().qualityGradeId())
                  && targets.sourcesCompatible(
                      context.target(),
                      metadata.getSources().stream()
                          .map(StockUnitCutPlan.Source::stockUnitId)
                          .toList()),
              actor,
              now);
      case CANCEL -> {
        if (request.reason() == null || request.reason().isBlank())
          throw StockUnitCutPlanException.invalid(
              "REASON_REQUIRED", "Cancelling a plan needs a reason");
        metadata.cancel(request.reason());
        allocations.releaseForLine(
            TenantContext.requireTenantId(),
            metadata.getTargetLineId(),
            actor,
            "CUT_PLAN_CANCELLED");
        holds.releaseHolds(metadata.getTargetLineId());
      }
      default ->
          throw StockUnitCutPlanException.invalid(
              "ACTION_INVALID", "Use the commercial Apply command to attach sources");
    }
    plans.saveAndFlush(metadata);
    targets.changed(context);
    return operations.complete(
        request.operationId(),
        actor,
        metadata.getTargetLineId(),
        hash,
        views.view(metadata, context));
  }

  private StockUnitCutPlan.Actual actual(Command command) {
    var value = command.actual();
    if (value == null)
      throw StockUnitCutPlanException.invalid(
          "ACTUAL_REQUIRED", "Record measured delivery, remainder and loss values");
    return new StockUnitCutPlan.Actual(
        value.deliveryLength(),
        value.remainderLength(),
        value.lossLength(),
        value.deliveryWeight(),
        value.remainderWeight(),
        value.lossWeight(),
        value.reason());
  }

  @Override
  @Transactional(readOnly = true)
  public List<StockUnitCutPlan.Source> confirmationSources(UUID line, String fingerprint) {
    return guard.active(line).stream()
        .findFirst()
        .map(
            plan -> {
              if (!Objects.equals(plan.getTargetFingerprint(), fingerprint)
                  || !sources.current(plan)
                  || !sources.ownsSources(plan))
                throw StockUnitCutPlanException.conflict(
                    "SOURCE_CHANGED", "A planned source or its target changed before confirmation");
              targets.assertFreeSources(
                  target(plan),
                  plan.getSources().stream().map(StockUnitCutPlan.Source::stockUnitId).toList(),
                  null);
              return plan.getSources().stream()
                  .map(
                      row ->
                          new StockUnitCutPlan.Source(
                              row.stockUnitId(),
                              row.batchId(),
                              sources.unit(row.stockUnitId()).getVersion(),
                              row.length(),
                              row.weight(),
                              row.weightUnit(),
                              row.serialNumber(),
                              row.qualityGradeId(),
                              row.measurementRevision()))
                  .toList();
            })
        .orElse(List.of());
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasUnfinishedPlan(UUID line) {
    return !guard.active(line).isEmpty()
        || plans
            .findByTenantIdAndTargetLineIdOrderByCreatedAtDescIdDesc(
                TenantContext.requireTenantId(), line)
            .stream()
            .filter(plan -> plan.getState() == StockUnitCutPlanState.COMPLETED)
            .anyMatch(
                plan -> {
                  var unit = sources.unit(plan.getDeliveryStockUnitId());
                  return unit.getQualityDisposition() != QualityDisposition.RELEASED
                      || unit.getStatus() == StockUnitStatus.ON_HOLD
                      || unit.getStatus() == StockUnitStatus.QUARANTINE;
                });
  }

  @Override
  @Transactional(readOnly = true)
  public boolean exactDeliveryReady(UUID line, java.math.BigDecimal quantity) {
    var allocated = allocations.activeForLine(TenantContext.requireTenantId(), line);
    if (allocated.isEmpty() || allocated.stream().anyMatch(row -> !"M".equals(row.unit())))
      return false;
    if (allocated.stream()
            .map(PieceAllocationPort.PieceAllocationView::quantity)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
            .compareTo(quantity)
        != 0) return false;
    return allocated.stream()
        .allMatch(
            row -> {
              var unit = sources.unit(row.stockUnitId());
              return sources.deliveryReady(unit, row.quantity());
            });
  }

  @Override
  @Transactional
  public void commercialLineChanged(UUID line) {
    guard.commercialChange(line);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Summary> currentForLine(UUID line) {
    return plans
        .findByTenantIdAndTargetLineIdOrderByCreatedAtDescIdDesc(
            TenantContext.requireTenantId(), line)
        .stream()
        .filter(plan -> plan.getState() != StockUnitCutPlanState.CANCELLED)
        .findFirst()
        .map(
            plan ->
                new Summary(
                    plan.getId(),
                    plan.getState(),
                    plan.sourceQuantity(),
                    plan.getDeliveryQuantity(),
                    plan.remainderQuantity(),
                    com.fabricmanagement.common.infrastructure.web.AppRoutes.stockCutPlan(
                        plan.getId()),
                    plan.getRecordedAt() != null
                            && plan.getState() != StockUnitCutPlanState.COMPLETED
                        || views.outputsHeld(plan)));
  }

  @Override
  @Transactional
  public void transfer(UUID oldLine, Context target) {
    guard
        .active(oldLine)
        .forEach(
            plan -> {
              if (plan.getDeliveryQuantity().compareTo(target.quantity()) != 0
                  || !plan.getProductId().equals(target.productId()))
                throw StockUnitCutPlanException.conflict(
                    "TARGET_CHANGED", "The converted target does not match the cut plan");
              plan.transfer(
                  target.target().type().name(),
                  target.target().documentId(),
                  target.target().lineId(),
                  target.fingerprint());
              plans.save(plan);
            });
  }

  private StockUnitCutPlan find(UUID id) {
    return plans
        .findByTenantIdAndId(TenantContext.requireTenantId(), id)
        .orElseThrow(
            () ->
                new com.fabricmanagement.common.infrastructure.web.exception.NotFoundException(
                    "Cut plan not found"));
  }

  private Target target(StockUnitCutPlan plan) {
    return new Target(
        TargetType.valueOf(plan.getTargetType()), plan.getTargetId(), plan.getTargetLineId());
  }

  private Map<UUID, java.math.BigDecimal> lotTotals(StockUnitCutPlan plan) {
    return plan.getSources().stream()
        .collect(
            Collectors.toMap(
                StockUnitCutPlan.Source::batchId,
                StockUnitCutPlan.Source::length,
                java.math.BigDecimal::add,
                TreeMap::new));
  }
}
