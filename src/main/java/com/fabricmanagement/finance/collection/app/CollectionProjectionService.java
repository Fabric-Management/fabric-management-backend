package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.app.port.CollectionSourcePort;
import com.fabricmanagement.finance.collection.domain.CollectionExpectation;
import com.fabricmanagement.finance.collection.domain.CollectionObligation;
import com.fabricmanagement.finance.collection.domain.CollectionProjection;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.infra.repository.CollectionExpectationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionObligationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionProjectionRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionProjectionService {
  private final CollectionSourcePort source;
  private final CollectionObligationRepository obligations;
  private final CollectionProjectionRepository projections;
  private final CollectionExpectationRepository expectations;
  private final CollectionCommandStore commands;
  private final CollectionEvents events;
  private final com.fabricmanagement.finance.collection.infra.repository
          .CollectionSourceObservationRepository
      observations;

  /**
   * Reconciliation always reads current accepted facts; delayed events cannot restore old terms.
   */
  @Transactional
  public void synchronize(UUID orderId) {
    commands.lock();
    UUID actor = TenantContext.getCurrentUserId();
    TenantContext.setCurrentUserId(TenantContext.SYSTEM_ACTOR_ID);
    try {
      rebuild(orderId);
    } catch (CollectionSourcePort.SourceBusyException busy) {
      throw CollectionRuleException.conflict(
          "COLLECTION_SOURCE_BUSY", "The order is being edited; try again shortly");
    } finally {
      TenantContext.setCurrentUserId(actor);
    }
  }

  /**
   * For a reader that can live with what finance last recorded: refreshes the projection unless a
   * writer holds the order right now, and never waits or fails because of it.
   *
   * @return {@code true} when the projection was refreshed, {@code false} when the order was busy
   *     and the last recorded projection stands
   */
  @Transactional
  public boolean synchronizeIfFree(UUID orderId) {
    commands.lock();
    return rebuildWhileFree(orderId);
  }

  /** What a reader that can do without a refresh learns about the attempt. */
  public enum Refresh {
    /** The projection was rebuilt from the source just now. */
    REFRESHED,
    /** A writer holds the finance tenant or the order; the last recorded projection stands. */
    BUSY,
    /** The source cannot be read as it is; the last recorded projection stands. */
    UNREADABLE
  }

  /**
   * For a screen: like {@link #synchronizeIfFree}, but it waits for nothing, not even the finance
   * tenant lock, and a source that cannot be read is an answer rather than an error. The caller's
   * transaction stays usable, and nothing was written when the answer is not {@link
   * Refresh#REFRESHED}: an unreadable source is refused before the first write.
   */
  @Transactional
  public Refresh refreshIfFree(UUID orderId) {
    if (!commands.tryLock()) return Refresh.BUSY;
    try {
      return rebuildWhileFree(orderId) ? Refresh.REFRESHED : Refresh.BUSY;
    } catch (CollectionRuleException refused) {
      String code = refused.getErrorCode();
      if ("COLLECTION_SOURCE_BUSY".equals(code)) return Refresh.BUSY;
      if (!"COLLECTION_SOURCE_INCOMPLETE".equals(code)
          && !"COLLECTION_SOURCE_IDENTITY_CHANGED".equals(code)) throw refused;
      log.warn(
          "Collection source of order {} cannot be read ({}): showing the recorded projection",
          orderId,
          code);
      return Refresh.UNREADABLE;
    }
  }

  private boolean rebuildWhileFree(UUID orderId) {
    if (!source.tryHold(TenantContext.requireTenantId(), orderId)) return false;
    synchronize(orderId);
    return true;
  }

  /**
   * Orders whose stamp equalled their projection when the source was listed at {@code listedAt}
   * were verified without being read: only their check time moves, so "last checked" keeps meaning
   * the last time finance confirmed the source, not the last time it rebuilt the projection.
   */
  @Transactional
  public void confirmUnchanged(Collection<UUID> orderIds, Instant listedAt) {
    if (orderIds.isEmpty()) return;
    commands.lock();
    projections.confirm(TenantContext.requireTenantId(), orderIds, listedAt);
  }

  /** Every row complete and unique, and no existing obligation asked to change who or what. */
  private static void requireReadableRows(
      CollectionSourcePort.Agreement agreement, List<CollectionObligation> current) {
    var logicalIds = new HashSet<UUID>();
    for (var line : agreement.rows())
      if (line.logicalId() == null
          || !logicalIds.add(line.logicalId())
          || line.method() == null
          || line.kind() == null
          || line.amount() == null
          || line.expectedDate() == null && !line.awaitsConfirmationDate()
          || line.method()
                  == com.fabricmanagement.finance.collection.domain.CollectionTypes.Method.CHEQUE
              && line.chequePaymentDate() == null)
        throw CollectionRuleException.conflict(
            "COLLECTION_SOURCE_INCOMPLETE",
            "Accepted payment rows must have unique identities, amounts and methods");
    for (var line : agreement.rows())
      for (var row : current)
        if (row.getLogicalId().equals(line.logicalId())
            && (!row.getPartnerId().equals(agreement.partnerId())
                || !row.getCurrency().equals(line.currency())))
          throw CollectionRuleException.conflict(
              "COLLECTION_SOURCE_IDENTITY_CHANGED",
              "A logical obligation cannot change partner or currency; reconcile explicitly");
  }

  private void rebuild(UUID orderId) {
    UUID tenant = TenantContext.requireTenantId();
    var found = source.find(tenant, orderId);
    var earlier = projections.findByTenantIdAndOrderId(tenant, orderId);
    if (found.isEmpty()) {
      earlier.ifPresent(
          value -> {
            if (!value.isSourceMissing())
              observations.saveAndFlush(
                  com.fabricmanagement.finance.collection.domain.CollectionSourceObservation.record(
                      orderId,
                      value.getPlanId(),
                      value.getRevisionId(),
                      value.getSourceVersion(),
                      value.isCancelled(),
                      value.isStale(),
                      true,
                      null,
                      commands.now(),
                      java.util.List.of()));
            value.missing(commands.now());
            projections.saveAndFlush(value);
          });
      return;
    }
    var agreement = found.get();
    // Whatever can make the source unreadable is judged before the first write, so a refusal
    // leaves nothing half-rebuilt and a reader may carry on in the same transaction.
    if (agreement.approvalId() == null
        || agreement.agreedAt() == null
        || agreement.activatedAt() == null
        || agreement.revisionId() == null
        || agreement.rows().isEmpty())
      throw CollectionRuleException.conflict(
          "COLLECTION_SOURCE_INCOMPLETE",
          "An operational agreement and confirmation proof are required");
    var projection =
        earlier.orElseGet(() -> CollectionProjection.create(orderId, agreement.planId()));
    if (!projection.getPlanId().equals(agreement.planId()))
      throw CollectionRuleException.conflict(
          "COLLECTION_SOURCE_IDENTITY_CHANGED",
          "A plan identity cannot replace existing receipt evidence");
    var current = obligations.findByTenantIdAndOrderId(tenant, orderId);
    requireReadableRows(agreement, current);
    var knownIds = current.stream().map(CollectionObligation::getId).toList();
    var recorded =
        knownIds.isEmpty()
            ? java.util.Set.<UUID>of()
            : expectations.recordedObligations(tenant, agreement.revisionId(), knownIds);
    var logicalIds = new HashSet<UUID>();
    for (var line : agreement.rows()) {
      logicalIds.add(line.logicalId());
      var row =
          current.stream()
              .filter(value -> value.getLogicalId().equals(line.logicalId()))
              .findFirst()
              .orElseGet(
                  () ->
                      CollectionObligation.create(
                          orderId,
                          agreement.planId(),
                          line.logicalId(),
                          agreement.partnerId(),
                          line.currency()));
      if (!agreement.revisionId().equals(row.getRevisionId())
          || row.isCancelled() != agreement.cancelled()) {
        row.revise(
            agreement.revisionId(),
            line.amount(),
            line.kind(),
            line.method(),
            line.expectedDate(),
            line.awaitsConfirmationDate(),
            line.chequePaymentDate(),
            agreement.activatedAt(),
            agreement.cancelled());
        obligations.saveAndFlush(row);
        events.changed(row.getId());
        if (!recorded.contains(row.getId()))
          expectations.saveAndFlush(
              CollectionExpectation.record(row, agreement.approvalId(), agreement.agreedAt()));
      }
    }
    current.stream()
        .filter(row -> !logicalIds.contains(row.getLogicalId()) && !row.isCancelled())
        .forEach(
            row -> {
              row.cancel();
              obligations.save(row);
              events.changed(row.getId());
            });
    obligations.flush();
    // A worker may first discover an agreement after its cancellation. Both source times are
    // known: retain activation too, so a later knowledge cut can reconstruct the pre-cancel day.
    if (projection.getRevisionId() == null
        && agreement.cancelled()
        && agreement.cancelledAt() != null) {
      observations.saveAndFlush(
          com.fabricmanagement.finance.collection.domain.CollectionSourceObservation.record(
              orderId,
              agreement.planId(),
              agreement.revisionId(),
              agreement.planVersion(),
              false,
              agreement.stale(),
              false,
              agreement.activatedAt(),
              commands.now(),
              agreement.rows().stream().map(value -> value.logicalId()).toList()));
    }
    if (projection.getRevisionId() == null
        || !projection.getRevisionId().equals(agreement.revisionId())
        || projection.isCancelled() != agreement.cancelled()
        || projection.isStale() != agreement.stale()
        || projection.isSourceMissing()) {
      observations.saveAndFlush(
          com.fabricmanagement.finance.collection.domain.CollectionSourceObservation.record(
              orderId,
              agreement.planId(),
              agreement.revisionId(),
              agreement.planVersion(),
              agreement.cancelled(),
              agreement.stale(),
              false,
              agreement.cancelled()
                  ? agreement.cancelledAt()
                  : !agreement.revisionId().equals(projection.getRevisionId())
                      ? agreement.activatedAt()
                      : null,
              commands.now(),
              agreement.cancelled()
                  ? java.util.List.of()
                  : agreement.rows().stream().map(value -> value.logicalId()).toList()));
    }
    projection.observed(
        agreement.revisionId(),
        agreement.planVersion(),
        agreement.orderVersion(),
        agreement.cancelled(),
        agreement.stale(),
        commands.now());
    projections.saveAndFlush(projection);
  }
}
