package com.fabricmanagement.production.core.stockunit.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.realtime.app.*;
import com.fabricmanagement.production.core.stockunit.api.StockUnitCutTargetPort;
import com.fabricmanagement.production.core.stockunit.api.StockUnitCutTargetPort.*;
import com.fabricmanagement.production.core.stockunit.api.StockUnitLeaseKeys;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitCutSession;
import com.fabricmanagement.production.core.stockunit.domain.exception.StockUnitCutPlanException;
import com.fabricmanagement.production.core.stockunit.dto.StockUnitCutPlanDtos;
import com.fabricmanagement.production.core.stockunit.dto.StockUnitCutSessionDtos.*;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitCutSessionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A target-bound adapter on the same stock-product sessions and stock-unit lease keys. */
@Service
@RequiredArgsConstructor
public class StockUnitCutSessionService {
  private final StockUnitCutSessionRepository repository;
  private final StockUnitCutTargetPort targets;
  private final StockUnitCutPlanService plans;
  private final StockUnitCutPlanSources sources;
  private final StockUnitCutPlanOperations operations;
  private final LiveEditSessionService sessions;
  private final LiveEditLeaseService leases;
  private final LiveEditLeaseProperties policy;
  private final EntityManager entityManager;
  private final Clock clock;
  private final StockUnitCutPlanGuard guard;
  private final com.fabricmanagement.production.core.stockunit.api.StockUnitOwnershipPort ownership;

  @Transactional
  public View open(Open request, UUID actor) {
    var context =
        targets.resolve(
            new Target(request.targetType(), request.documentId(), request.lineId()), actor, true);
    requireEditable(context);
    ownership.lockProduct(context.productId());
    var session = sessions.open(StockUnitLeaseKeys.resource(context.productId()), actor);
    var row =
        StockUnitCutSession.open(
            session.getId(),
            actor,
            request.targetType().name(),
            request.documentId(),
            request.lineId(),
            context.productId(),
            context.fingerprint());
    guard.latest(request.lineId()).ifPresent(plan -> row.bindPlan(plan.getId(), plan.getVersion()));
    var saved = repository.saveAndFlush(row);
    return view(saved, context);
  }

  @Transactional
  public View status(UUID id, UUID actor) {
    var row = load(id, actor);
    return view(row, targets.resolve(target(row), actor, false));
  }

  @Transactional
  public View pick(UUID id, Pick request, UUID actor) {
    var row = load(id, actor);
    var context = targets.resolve(target(row), actor, true);
    requireEditable(context);
    ownership.lockProduct(context.productId());
    lock(row, request.expectedRevision());
    if (!row.getTargetFingerprint().equals(context.fingerprint())) throw changed();
    sessions.renew(StockUnitLeaseKeys.resource(row.getProductId()), row.getSessionId(), actor);
    var evidence =
        sources.capture(request.stockUnitIds(), row.getTargetLineId(), row.getSessionId());
    if (!targets.sourcesCompatible(target(row), request.stockUnitIds())) throw changed();
    targets.assertFreeSources(target(row), request.stockUnitIds(), row.getSessionId());
    var granted =
        leases.replace(
            StockUnitLeaseKeys.resource(row.getProductId()),
            StockUnitLeaseKeys.GENERATION,
            row.getSessionId(),
            actor,
            request.stockUnitIds().stream().map(StockUnitLeaseKeys::key).toList());
    if (!(granted instanceof LiveEditLeaseService.Granted)) throw changed();
    BigDecimal total =
        evidence.stream()
            .map(
                com.fabricmanagement.production.core.stockunit.domain.StockUnitCutPlan.Source
                    ::length)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var cut = request.cutSourceId();
    if (cut == null && total.compareTo(context.quantity()) > 0)
      cut =
          evidence.stream()
              .filter(source -> source.length().compareTo(total.subtract(context.quantity())) > 0)
              .reduce((first, last) -> last)
              .map(
                  com.fabricmanagement.production.core.stockunit.domain.StockUnitCutPlan.Source
                      ::stockUnitId)
              .orElse(null);
    // The same measured evidence must still exist at Apply; a lease alone is insufficient.
    row.remember(
        request.stockUnitIds(), cut, operations.hash(List.of(context.fingerprint(), evidence)));
    row = repository.saveAndFlush(row);
    return view(row, context);
  }

  @Transactional
  public View renew(UUID id, Tokens request, UUID actor) {
    var row = load(id, actor);
    var context = targets.resolve(target(row), actor, true);
    requireEditable(context);
    ownership.lockProduct(context.productId());
    lock(row, request.expectedRevision());
    prove(row, request.tokens());
    sessions.renew(StockUnitLeaseKeys.resource(row.getProductId()), row.getSessionId(), actor);
    var renewal =
        leases.renew(
            StockUnitLeaseKeys.resource(row.getProductId()),
            StockUnitLeaseKeys.GENERATION,
            row.getSessionId(),
            actor,
            request.tokens());
    if (!renewal.lost().isEmpty()) throw changed();
    return view(row, context);
  }

  @Transactional
  public View close(UUID id, Tokens request, UUID actor) {
    var row = load(id, actor);
    var context = targets.resolve(target(row), actor, true);
    if (row.isEnded()) return view(row, context);
    ownership.lockProduct(context.productId());
    lock(row, request.expectedRevision());
    if (hasLivePeriods(row)) prove(row, request.tokens());
    sessions.close(StockUnitLeaseKeys.resource(row.getProductId()), row.getSessionId(), actor);
    row.end();
    return view(repository.saveAndFlush(row), context);
  }

  @Transactional
  public View release(UUID id, Tokens request, UUID actor) {
    var row = load(id, actor);
    var context = targets.resolve(target(row), actor, true);
    if (row.isEnded()) return view(row, context);
    ownership.lockProduct(context.productId());
    lock(row, request.expectedRevision());
    if (hasLivePeriods(row)) prove(row, request.tokens());
    leases.release(
        StockUnitLeaseKeys.resource(row.getProductId()),
        row.getSessionId(),
        actor,
        request.tokens());
    row.remember(List.of(), null, null);
    return view(repository.saveAndFlush(row), context);
  }

  private boolean hasLivePeriods(StockUnitCutSession row) {
    return leases
        .held(StockUnitLeaseKeys.resource(row.getProductId()), StockUnitLeaseKeys.GENERATION)
        .stream()
        .anyMatch(
            lease ->
                lease.getSessionId().equals(row.getSessionId())
                    && lease.getUserId().equals(row.getActorId()));
  }

  @Transactional
  public StockUnitCutPlanDtos.View apply(UUID id, Apply request, UUID actor) {
    var row = load(id, actor);
    var context = targets.resolve(target(row), actor, true);
    String hash = operations.hash(List.of("CUT_APPLY", id, request));
    ownership.lockProduct(context.productId());
    var replay = plans.replay(request.operationId(), actor, row.getTargetLineId(), hash);
    if (replay != null) return replay;
    requireEditable(context);
    lock(row, request.expectedRevision());
    if (!Objects.equals(context.documentVersion(), request.expectedDocumentVersion())
        || !row.getTargetFingerprint().equals(context.fingerprint())) throw changed();
    var previous = guard.latest(row.getTargetLineId());
    if (!Objects.equals(
            row.getPreviousPlanId(),
            previous
                .map(com.fabricmanagement.production.core.stockunit.domain.StockUnitCutPlan::getId)
                .orElse(null))
        || !Objects.equals(
            row.getPreviousPlanVersion(),
            previous
                .map(
                    com.fabricmanagement.production.core.stockunit.domain.StockUnitCutPlan
                        ::getVersion)
                .orElse(null))) throw changed();
    prove(row, request.tokens());
    var evidence = sources.capture(row.getSourceIds(), row.getTargetLineId(), row.getSessionId());
    if (!Objects.equals(
        row.getEvidenceHash(), operations.hash(List.of(context.fingerprint(), evidence))))
      throw changed();
    if (row.getCutSourceId() == null)
      throw StockUnitCutPlanException.invalid(
          "CUT_SOURCE_REQUIRED", "Select enough source rolls for an explicit cut plan");
    var result =
        plans.apply(
            context,
            row.getSourceIds(),
            row.getCutSourceId(),
            row.getSessionId(),
            request.operationId(),
            actor,
            hash);
    leases.release(
        StockUnitLeaseKeys.resource(row.getProductId()),
        row.getSessionId(),
        actor,
        request.tokens());
    row.end();
    repository.save(row);
    return result;
  }

  private void prove(StockUnitCutSession row, List<UUID> tokens) {
    var proof =
        leases.verify(
            StockUnitLeaseKeys.resource(row.getProductId()),
            StockUnitLeaseKeys.GENERATION,
            row.getSessionId(),
            Set.copyOf(tokens),
            row.getActorId(),
            row.getSourceIds().stream().map(StockUnitLeaseKeys::key).collect(Collectors.toSet()));
    if (!proof.proven() || row.getSourceIds().isEmpty()) throw changed();
  }

  private void lock(StockUnitCutSession row, Long expected) {
    entityManager.refresh(row, LockModeType.PESSIMISTIC_WRITE);
    if (row.isEnded() || !Objects.equals(row.getVersion(), expected)) throw changed();
  }

  private View view(StockUnitCutSession row, Context context) {
    var held =
        leases
            .held(StockUnitLeaseKeys.resource(row.getProductId()), StockUnitLeaseKeys.GENERATION)
            .stream()
            .filter(
                lease ->
                    lease.getSessionId().equals(row.getSessionId())
                        && lease.getUserId().equals(row.getActorId()))
            .toList();
    var expiry =
        held.stream()
            .map(com.fabricmanagement.platform.realtime.domain.LiveEditLease::getExpiresAt)
            .min(Comparator.naturalOrder())
            .orElse(null);
    long remaining =
        expiry == null ? 0 : Math.max(0, Duration.between(clock.instant(), expiry).toMillis());
    StockUnitCutPlanDtos.View evaluated = null;
    Reason reason =
        row.isEnded()
            ? Reason.SELECTION_ENDED
            : row.getSourceIds().isEmpty()
                ? Reason.SELECT_SOURCES
                : held.size() != row.getSourceIds().size() || held.isEmpty()
                    ? Reason.OWNERSHIP_REQUIRED
                    : Reason.REFRESH_REQUIRED;
    if (!row.isEnded()
        && held.size() == row.getSourceIds().size()
        && !held.isEmpty()
        && row.getTargetFingerprint().equals(context.fingerprint())
        && context.editable()) {
      try {
        var current =
            sources.capture(row.getSourceIds(), row.getTargetLineId(), row.getSessionId());
        if (Objects.equals(
            row.getEvidenceHash(), operations.hash(List.of(context.fingerprint(), current)))) {
          if (row.getCutSourceId() != null) {
            evaluated =
                plans.evaluate(
                    context,
                    row.getSourceIds(),
                    row.getCutSourceId(),
                    row.getSessionId(),
                    row.getActorId());
            reason = null;
          } else {
            var total =
                current.stream()
                    .map(
                        com.fabricmanagement.production.core.stockunit.domain.StockUnitCutPlan
                                .Source
                            ::length)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            reason =
                total.compareTo(context.quantity()) < 0
                    ? Reason.INSUFFICIENT_SOURCES
                    : total.compareTo(context.quantity()) == 0
                        ? Reason.WHOLE_ROLLS_MATCH
                        : Reason.CUT_SOURCE_REQUIRED;
          }
        }
      } catch (com.fabricmanagement.common.infrastructure.web.exception.DomainException changed) {
        // Preserve the picked IDs, but do not advertise an applicable plan from stale evidence.
      }
    }
    var state =
        row.isEnded()
            ? State.ENDED
            : !held.isEmpty()
                ? State.HELD
                : row.getSourceIds().isEmpty() ? State.OPEN : State.EXPIRED;
    return new View(
        row.getId(),
        row.getSessionId(),
        row.getVersion(),
        context.documentVersion(),
        state,
        new Policy(
            policy.ttlSeconds(),
            policy.renewAfterSeconds(),
            policy.idleAfterSeconds(),
            policy.idleWarningSeconds(),
            policy.getMaxKeysPerRequest()),
        held.stream()
            .map(
                lease ->
                    new Grant(
                        StockUnitLeaseKeys.pieceId(lease.key()),
                        lease.getToken(),
                        lease.getExpiresAt()))
            .toList(),
        remaining,
        expiry,
        row.getSourceIds(),
        sources.candidates(context, row.getSessionId()),
        reason,
        evaluated);
  }

  private StockUnitCutSession load(UUID id, UUID actor) {
    return repository
        .findByTenantIdAndIdAndActorId(TenantContext.requireTenantId(), id, actor)
        .orElseThrow(
            () ->
                new com.fabricmanagement.common.infrastructure.web.exception.NotFoundException(
                    "Cut selection not found"));
  }

  private Target target(StockUnitCutSession row) {
    return new Target(
        TargetType.valueOf(row.getTargetType()), row.getTargetId(), row.getTargetLineId());
  }

  private void requireEditable(Context context) {
    if (!context.canPrepare())
      throw new org.springframework.security.access.AccessDeniedException(
          "Current planning access is required");
    if (!context.active() || !context.editable()) throw changed();
  }

  private StockUnitCutPlanException changed() {
    return StockUnitCutPlanException.conflict(
        "SELECTION_CHANGED", "Reload the current target and source selection before applying");
  }
}
