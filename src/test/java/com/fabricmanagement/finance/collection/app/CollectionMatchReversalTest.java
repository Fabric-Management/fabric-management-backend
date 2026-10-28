package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.domain.CollectionObligation;
import com.fabricmanagement.finance.collection.domain.CollectionPaymentMatch;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.infra.repository.CollectionObligationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository;
import com.fabricmanagement.finance.period.app.port.FinancialPeriodGuard;
import com.fabricmanagement.finance.period.domain.exception.ClosedFinancialPeriodException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CollectionMatchReversalTest {
  final UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
  final LocalDate matchDate = LocalDate.of(2026, 10, 2), on = LocalDate.of(2026, 10, 20);
  final Instant now = Instant.parse("2026-10-20T12:00:00Z");
  CollectionCommandStore commands;
  CollectionObligationRepository obligations;
  CollectionPaymentMatchRepository matches;
  CollectionEvents events;
  FinancialPeriodGuard periods;
  CollectionMatchReversal reversal;
  CollectionObligation row;
  CollectionPaymentMatch match;

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    commands = mock(CollectionCommandStore.class);
    obligations = mock(CollectionObligationRepository.class);
    matches = mock(CollectionPaymentMatchRepository.class);
    events = mock(CollectionEvents.class);
    periods = mock(FinancialPeriodGuard.class);
    reversal = new CollectionMatchReversal(commands, obligations, matches, events, periods);
    row =
        CollectionObligation.create(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "GBP");
    row.setId(UUID.randomUUID());
    match =
        CollectionPaymentMatch.create(
            row.getId(),
            UUID.randomUUID(),
            null,
            new BigDecimal("100.00"),
            matchDate,
            Instant.parse("2026-10-02T09:00:00Z"),
            actor,
            "Received");
    when(commands.now()).thenReturn(now);
    when(obligations.findByTenantIdAndId(tenant, row.getId())).thenReturn(Optional.of(row));
  }

  @AfterEach
  void cleanup() {
    TenantContext.clear();
  }

  @Test
  void postsTheReversalAsOfTheGivenDateAndTellsTheObligationItChanged() {
    reversal.reverse(match, on, now, actor, "Paid twice");

    assertThat(match.getReversedOn()).isEqualTo(on);
    assertThat(match.getReversedAt()).isEqualTo(now);
    assertThat(match.getReversedBy()).isEqualTo(actor);
    assertThat(match.getReversalReason()).isEqualTo("Paid twice");
    verify(periods).assertPostingAllowed(tenant, on);
    verify(matches).saveAndFlush(match);
    verify(obligations).saveAndFlush(row);
    verify(events).changed(row.getId());
  }

  @Test
  void aClosedPeriodStopsTheReversalBeforeTheMatchChanges() {
    doThrow(new ClosedFinancialPeriodException(on, YearMonth.from(on)))
        .when(periods)
        .assertPostingAllowed(tenant, on);

    assertThatThrownBy(() -> reversal.reverse(match, on, now, actor, "Late"))
        .isInstanceOf(ClosedFinancialPeriodException.class);

    assertThat(match.getReversedAt()).isNull();
    verify(matches, never()).saveAndFlush(any());
    verify(events, never()).changed(any());
  }

  @Test
  void aDateBeforeTheMatchIsRefusedAndNothingIsSaved() {
    assertThatThrownBy(() -> reversal.reverse(match, matchDate.minusDays(1), now, actor, "Early"))
        .isInstanceOfSatisfying(
            CollectionRuleException.class,
            refused -> {
              assertThat(refused.getErrorCode()).isEqualTo("COLLECTION_DATE_BEFORE_MATCH");
              assertThat(refused.getDetails()).containsEntry("earliestDate", matchDate.toString());
              // The localized message fills the day in from its first argument.
              assertThat(refused.getArgs()).containsExactly(matchDate.toString());
            });

    assertThat(match.getReversedAt()).isNull();
    verify(matches, never()).saveAndFlush(any());
  }
}
