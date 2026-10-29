package com.fabricmanagement.finance.collection.app.adapter;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantBusinessCalendarPort;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.finance.collection.app.*;
import com.fabricmanagement.finance.collection.domain.*;
import com.fabricmanagement.finance.collection.infra.repository.*;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import com.fabricmanagement.finance.common.app.FinancePostingDates;
import com.fabricmanagement.flowboard.collection.app.port.CollectionFollowUpPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CollectionFollowUpAdapter implements CollectionFollowUpPort {
  private final com.fabricmanagement.platform.tradingpartner.app.TradingPartnerQueryService
      partners;
  private final CollectionObligationRepository obligations;
  private final CollectionProjectionRepository projections;
  private final CollectionPaymentMatchRepository matches;
  private final CollectionChequeApplicationRepository cheques;
  private final CollectionCommandStore commands;
  private final CollectionAccessService access;
  private final FinancePostingDates dates;
  private final TenantBusinessCalendarPort calendar;
  private final CollectionReconciliationHealthService health;

  @Override
  public boolean upstreamStale() {
    return health.stale();
  }

  @Override
  public ReadScope readScope(UUID actor) {
    var reach = access.reach(actor, "read");
    return new ReadScope(
        reach.scope() == com.fabricmanagement.platform.user.domain.DataScope.GLOBAL
            || reach.scope() == com.fabricmanagement.platform.user.domain.DataScope.ORGANIZATION,
        reach.scope() == null ? Set.of() : reach.principals());
  }

  @Override
  public String partnerName(UUID partner) {
    return partners.findDisplayName(TenantContext.requireTenantId(), partner).orElse(null);
  }

  @Override
  public void lock() {
    commands.lock();
  }

  private CollectionObligation row(UUID id) {
    return obligations
        .findByTenantIdAndId(TenantContext.requireTenantId(), id)
        .orElseThrow(() -> new NotFoundException("Collection obligation not found"));
  }

  @Override
  @Transactional
  public Facts current(UUID id) {
    lock();
    var row = row(id);
    // What finance last recorded. A reader never rebuilds the projection: events and the
    // reconciliation do, and a scan over many obligations must cost reads only, never a wait for
    // an order that is being edited.
    UUID tenant = TenantContext.requireTenantId();
    var state = projections.findByTenantIdAndOrderId(tenant, row.getOrderId()).orElse(null);
    BigDecimal cash =
        matches.settled(tenant, List.of(id)).stream()
            .findFirst()
            .map(CollectionPaymentMatchRepository.Balance::getAmount)
            .orElse(BigDecimal.ZERO);
    BigDecimal received =
        cheques.received(tenant, List.of(id)).stream()
            .findFirst()
            .map(CollectionPaymentMatchRepository.Balance::getAmount)
            .orElse(BigDecimal.ZERO);
    BigDecimal nonCheque =
        matches.nonChequeSettledFor(tenant, List.of(id)).stream()
            .findFirst()
            .map(CollectionPaymentMatchRepository.Balance::getAmount)
            .orElse(BigDecimal.ZERO);
    return new Facts(
        id,
        row.getVersion(),
        row.getPartnerId(),
        row.getCurrency(),
        row.getAmount(),
        CollectionAmounts.outstanding(row.getAmount(), cash),
        row.getMethod() == CollectionTypes.Method.CHEQUE
            ? CollectionAmounts.outstanding(row.getAmount(), received.add(nonCheque))
            : BigDecimal.ZERO,
        row.getExpectedDate(),
        row.getChequePaymentDate(),
        row.getMethod() == CollectionTypes.Method.CHEQUE,
        row.isCancelled(),
        state == null || state.isSourceMissing(),
        state != null && state.isStale(),
        row.getResponsibleUserId());
  }

  @Override
  @Transactional(readOnly = true)
  public List<UUID> discover(UUID after, int limit) {
    return obligations.findOpenDueIds(
        TenantContext.requireTenantId(), today(), after, Math.min(limit, 100));
  }

  @Override
  @Transactional(readOnly = true)
  public Set<UUID> closed(List<UUID> ids) {
    if (ids.isEmpty()) return Set.of();
    UUID tenant = TenantContext.requireTenantId();
    var rows = obligations.findByTenantIdAndIdIn(tenant, ids);
    var settled =
        matches.settled(tenant, ids).stream()
            .collect(
                Collectors.toMap(
                    CollectionPaymentMatchRepository.Balance::getId,
                    CollectionPaymentMatchRepository.Balance::getAmount));
    var states =
        projections
            .findByTenantIdAndOrderIdIn(
                tenant, rows.stream().map(CollectionObligation::getOrderId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(CollectionProjection::getOrderId, state -> state));
    // The same judgement as Facts.closed(): a source that cannot be seen closes nothing.
    return rows.stream()
        .filter(
            row -> {
              var state = states.get(row.getOrderId());
              if (state == null || state.isSourceMissing()) return false;
              var cash = settled.getOrDefault(row.getId(), BigDecimal.ZERO);
              return row.isCancelled()
                  || CollectionAmounts.outstanding(row.getAmount(), cash).signum() == 0;
            })
        .map(CollectionObligation::getId)
        .collect(Collectors.toSet());
  }

  @Override
  public boolean mayRead(UUID actor, UUID id) {
    var row = row(id);
    return access
        .reach(actor, "read")
        .permits(row.getTenantId(), row.getCreatedBy(), row.getResponsibleUserId());
  }

  @Override
  public boolean eligible(UUID actor, UUID id) {
    // A routing appointment confers responsibility, never a new finance permission.
    // OWN/DEPARTMENT candidates are evaluated against the proposed responsibility explicitly.
    var row = row(id);
    return access.reach(actor, "read").permits(row.getTenantId(), row.getCreatedBy(), actor);
  }

  @Override
  @Transactional
  public void assign(UUID id, UUID owner) {
    lock();
    var row = row(id);
    if (!Objects.equals(owner, row.getResponsibleUserId())) {
      row.assignResponsible(owner);
      obligations.saveAndFlush(row);
    }
  }

  @Override
  public LocalDate today() {
    return dates.today();
  }

  @Override
  public LocalDate dayOf(java.time.Instant occurredAt) {
    return occurredAt.atZone(calendar.zone()).toLocalDate();
  }
}
