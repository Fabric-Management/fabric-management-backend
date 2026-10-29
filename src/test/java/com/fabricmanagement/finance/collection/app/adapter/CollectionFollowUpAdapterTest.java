package com.fabricmanagement.finance.collection.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantBusinessCalendarPort;
import com.fabricmanagement.finance.collection.app.CollectionCommandStore;
import com.fabricmanagement.finance.collection.app.CollectionReconciliationHealthService;
import com.fabricmanagement.finance.collection.domain.CollectionObligation;
import com.fabricmanagement.finance.collection.domain.CollectionProjection;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Kind;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Method;
import com.fabricmanagement.finance.collection.infra.repository.CollectionChequeApplicationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionObligationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository.Balance;
import com.fabricmanagement.finance.collection.infra.repository.CollectionProjectionRepository;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import com.fabricmanagement.finance.common.app.FinancePostingDates;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerQueryService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CollectionFollowUpAdapterTest {
  final UUID tenant = UUID.randomUUID();
  final Instant now = Instant.parse("2026-10-22T23:30:00Z");
  CollectionObligationRepository obligations;
  CollectionProjectionRepository projections;
  CollectionPaymentMatchRepository matches;
  CollectionChequeApplicationRepository cheques;
  CollectionCommandStore commands;
  CollectionFollowUpAdapter adapter;

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    obligations = mock(CollectionObligationRepository.class);
    projections = mock(CollectionProjectionRepository.class);
    matches = mock(CollectionPaymentMatchRepository.class);
    cheques = mock(CollectionChequeApplicationRepository.class);
    commands = mock(CollectionCommandStore.class);
    TenantBusinessCalendarPort calendar = () -> ZoneId.of("Europe/London");
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    adapter =
        new CollectionFollowUpAdapter(
            mock(TradingPartnerQueryService.class),
            obligations,
            projections,
            matches,
            cheques,
            commands,
            mock(CollectionAccessService.class),
            new FinancePostingDates(clock, calendar),
            calendar,
            mock(CollectionReconciliationHealthService.class));
  }

  @AfterEach
  void cleanup() {
    TenantContext.clear();
  }

  private CollectionObligation obligation(UUID order, String amount, boolean cancelled) {
    var row =
        CollectionObligation.create(
            order, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "GBP");
    row.setId(UUID.randomUUID());
    row.setTenantId(tenant);
    row.revise(
        UUID.randomUUID(),
        new BigDecimal(amount),
        Kind.INSTALMENT,
        Method.BANK_TRANSFER,
        LocalDate.of(2026, 10, 20),
        false,
        null,
        now,
        cancelled);
    return row;
  }

  private CollectionProjection recorded(UUID order, boolean sourceMissing) {
    var state = mock(CollectionProjection.class);
    when(state.getOrderId()).thenReturn(order);
    when(state.isSourceMissing()).thenReturn(sourceMissing);
    return state;
  }

  private static Balance balance(UUID id, String amount) {
    var value = mock(Balance.class);
    when(value.getId()).thenReturn(id);
    when(value.getAmount()).thenReturn(new BigDecimal(amount));
    return value;
  }

  @Test
  void theFactsAreTheLastRecordedOnesTakenUnderTheTenantLockAndNothingIsRebuilt() {
    UUID order = UUID.randomUUID();
    var row = obligation(order, "100.00", false);
    when(obligations.findByTenantIdAndId(tenant, row.getId())).thenReturn(Optional.of(row));
    var projection = recorded(order, false);
    when(projections.findByTenantIdAndOrderId(tenant, order)).thenReturn(Optional.of(projection));
    var cash = balance(row.getId(), "40.00");
    when(matches.settled(tenant, List.of(row.getId()))).thenReturn(List.of(cash));

    var facts = adapter.current(row.getId());

    verify(commands).lock();
    assertThat(facts.expected()).isEqualByComparingTo("100.00");
    assertThat(facts.outstanding()).isEqualByComparingTo("60.00");
    assertThat(facts.sourceMissing()).isFalse();
    assertThat(facts.closed()).isFalse();
  }

  @Test
  void aProjectionNobodyHasRecordedMeansTheSourceIsMissing() {
    UUID order = UUID.randomUUID();
    var row = obligation(order, "100.00", false);
    when(obligations.findByTenantIdAndId(tenant, row.getId())).thenReturn(Optional.of(row));
    when(projections.findByTenantIdAndOrderId(tenant, order)).thenReturn(Optional.empty());

    assertThat(adapter.current(row.getId()).sourceMissing()).isTrue();
  }

  @Test
  void discoveryAsksForTheOpenDueOnesOnTheTenantsBusinessDayAndCapsThePage() {
    UUID after = UUID.randomUUID(), next = UUID.randomUUID();
    // 23:30 UTC on the 22nd is already the 23rd in London during British Summer Time.
    when(obligations.findOpenDueIds(tenant, LocalDate.of(2026, 10, 23), after, 100))
        .thenReturn(List.of(next));

    assertThat(adapter.discover(after, 5_000)).containsExactly(next);
  }

  @Test
  void onlySettledOrCancelledObligationsOfAVisibleSourceAreClosed() {
    UUID orderA = UUID.randomUUID(), orderB = UUID.randomUUID(), orderC = UUID.randomUUID();
    var settled = obligation(orderA, "100.00", false);
    var partly = obligation(orderA, "100.00", false);
    var cancelled = obligation(orderB, "100.00", true);
    var unseen = obligation(orderC, "100.00", false);
    var ids = List.of(settled.getId(), partly.getId(), cancelled.getId(), unseen.getId());
    when(obligations.findByTenantIdAndIdIn(tenant, ids))
        .thenReturn(List.of(settled, partly, cancelled, unseen));
    var cash = List.of(balance(settled.getId(), "100.00"), balance(partly.getId(), "40.00"));
    when(matches.settled(tenant, ids)).thenReturn(cash);
    var recordedSources = List.of(recorded(orderA, false), recorded(orderB, false));
    when(projections.findByTenantIdAndOrderIdIn(any(), any())).thenReturn(recordedSources);

    assertThat(adapter.closed(ids)).containsExactlyInAnyOrder(settled.getId(), cancelled.getId());
  }

  @Test
  void anObligationWhoseSourceIsMissingIsNeverClosedHoweverSettledItLooks() {
    UUID order = UUID.randomUUID();
    var settled = obligation(order, "100.00", false);
    var ids = List.of(settled.getId());
    when(obligations.findByTenantIdAndIdIn(tenant, ids)).thenReturn(List.of(settled));
    var cash = balance(settled.getId(), "100.00");
    when(matches.settled(tenant, ids)).thenReturn(List.of(cash));
    var projection = recorded(order, true);
    when(projections.findByTenantIdAndOrderIdIn(any(), any())).thenReturn(List.of(projection));

    assertThat(adapter.closed(ids)).isEmpty();
  }

  @Test
  void askingAboutNoObligationsReadsNothing() {
    assertThat(adapter.closed(List.of())).isEqualTo(Set.of());

    verifyNoInteractions(obligations, matches, projections);
    verify(commands, never()).lock();
  }
}
