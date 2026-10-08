package com.fabricmanagement.platform.realtime.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseService.Granted;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseService.Refused;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseService.Verification;
import com.fabricmanagement.platform.realtime.domain.LiveEditLease;
import com.fabricmanagement.platform.realtime.domain.LiveEditSession;
import com.fabricmanagement.platform.realtime.domain.LiveLeaseKey;
import com.fabricmanagement.platform.realtime.domain.LiveLeaseMode;
import com.fabricmanagement.platform.realtime.domain.LiveResource;
import com.fabricmanagement.platform.realtime.domain.LiveRevision;
import com.fabricmanagement.platform.realtime.domain.exception.LiveEditLeaseLimitException;
import com.fabricmanagement.platform.realtime.domain.exception.LiveEditLeasesNotEnforcedException;
import com.fabricmanagement.platform.realtime.domain.exception.LiveEditSessionNotFoundException;
import com.fabricmanagement.platform.realtime.infra.repository.LiveEditLeaseControlRepository;
import com.fabricmanagement.platform.realtime.infra.repository.LiveEditLeaseRepository;
import com.fabricmanagement.platform.realtime.infra.repository.LiveEditSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * The lease engine's decisions (CEDIT-07 §3), with the repositories mocked. Locking, RLS and races
 * on real PostgreSQL are proven in {@code SalesOrderEditLeaseIT}.
 */
class LiveEditLeaseServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");
  private static final String HEADER = "header";

  private final UUID tenant = UUID.randomUUID();
  private final UUID user = UUID.randomUUID();
  private final UUID otherUser = UUID.randomUUID();
  private final LiveResource order = new LiveResource("sales-order", UUID.randomUUID());
  private final LiveLeaseKey notes = new LiveLeaseKey(HEADER, "notes");
  private final LiveLeaseKey terms = new LiveLeaseKey(HEADER, "paymentTerms");
  private final String lineScope = "line:" + UUID.randomUUID();
  private final LiveLeaseKey quantity = new LiveLeaseKey(lineScope, "line.quantity");
  private final LiveLeaseKey wholeLine = LiveLeaseKey.whole(lineScope);

  private final LiveEditLeaseRepository leases = mock(LiveEditLeaseRepository.class);
  private final LiveEditSessionRepository sessions = mock(LiveEditSessionRepository.class);
  private final LiveEditLeaseControlRepository control = mock(LiveEditLeaseControlRepository.class);
  private LiveEditLeaseProperties properties;
  private LiveEditLeaseService service;
  private LiveEditSession mine;
  private LiveEditSession theirs;

  @BeforeEach
  void setUp() {
    properties = new LiveEditLeaseProperties();
    properties.afterPropertiesSet();
    service =
        new LiveEditLeaseService(
            leases, sessions, control, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    TenantContext.setCurrentTenantId(tenant);
    mine = session(user, Duration.ofMinutes(10));
    theirs = session(otherUser, Duration.ofMinutes(10));
    when(control.isEnforced(tenant, "sales-order")).thenReturn(true);
    when(leases.save(any())).thenAnswer(call -> withId(call.getArgument(0)));
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("L01: a free key is granted with a token; the resource lock comes before the rows")
  void grantsAFreeKey() {
    rows(List.of());

    Granted granted = (Granted) service.acquire(order, 0, mine.getId(), user, List.of(notes));

    LiveEditLease lease = granted.leases().getFirst();
    assertThat(lease.key()).isEqualTo(notes);
    assertThat(lease.getToken()).isNotNull();
    assertThat(lease.getExpiresAt()).isEqualTo(NOW.plusSeconds(90));
    InOrder sequence = inOrder(control, sessions, leases);
    sequence.verify(control).lockResource(tenant, order);
    sequence.verify(sessions).lockForLease(tenant, mine.getId(), "sales-order", order.id());
    sequence.verify(leases).lockInScopes(eq(tenant), eq("sales-order"), eq(order.id()), any());
  }

  @Test
  @DisplayName("L01/L02: another session's held key refuses the whole request without its token")
  void refusesAllOrNothing() {
    LiveEditLease held = lease(notes, theirs, NOW.plusSeconds(60));
    rows(List.of(held));

    Refused refused =
        (Refused) service.acquire(order, 0, mine.getId(), user, List.of(notes, terms));

    assertThat(refused.holders()).containsExactly(held);
    verify(leases, never()).save(any());
    assertThat(held.getSessionId()).isEqualTo(theirs.getId());
  }

  @Test
  @DisplayName("L02 (CE-10): the same person's other tab is another session and is refused too")
  void otherTabOfTheSamePersonIsRefused() {
    LiveEditSession otherTab = session(user, Duration.ofMinutes(10));
    rows(List.of(lease(notes, otherTab, NOW.plusSeconds(60))));

    assertThat(service.acquire(order, 0, mine.getId(), user, List.of(notes)))
        .isInstanceOf(Refused.class);
  }

  @Test
  @DisplayName("L04: a whole line and a key of the same line exclude each other, both ways")
  void wholeLineAndFieldExcludeEachOther() {
    rows(List.of(lease(quantity, theirs, NOW.plusSeconds(60))));
    assertThat(service.acquire(order, 0, mine.getId(), user, List.of(wholeLine)))
        .isInstanceOf(Refused.class);

    rows(List.of(lease(wholeLine, theirs, NOW.plusSeconds(60))));
    assertThat(service.acquire(order, 0, mine.getId(), user, List.of(quantity)))
        .isInstanceOf(Refused.class);
  }

  @Test
  @DisplayName("L19: a repeat after a lost answer keeps the period and its token")
  void repeatKeepsTheToken() {
    LiveEditLease own = lease(notes, mine, NOW.plusSeconds(30));
    UUID token = own.getToken();
    Instant acquired = own.getAcquiredAt();
    rows(List.of(own));

    Granted again = (Granted) service.acquire(order, 0, mine.getId(), user, List.of(notes));

    assertThat(again.leases().getFirst().getToken()).isEqualTo(token);
    assertThat(again.leases().getFirst().getAcquiredAt()).isEqualTo(acquired);
    assertThat(again.leases().getFirst().getExpiresAt()).isEqualTo(NOW.plusSeconds(90));
  }

  @Test
  @DisplayName("L05: an expired or released key is taken over with a new token")
  void endedKeyIsTakenOver() {
    LiveEditLease expired = lease(notes, theirs, NOW.minusSeconds(1));
    UUID old = expired.getToken();
    rows(List.of(expired));

    Granted granted = (Granted) service.acquire(order, 0, mine.getId(), user, List.of(notes));

    assertThat(granted.leases().getFirst()).isSameAs(expired);
    assertThat(expired.getSessionId()).isEqualTo(mine.getId());
    assertThat(expired.getToken()).isNotEqualTo(old);
  }

  @Test
  @DisplayName("L16: a lease of an older order epoch is not in the way")
  void olderGenerationIsNotInTheWay() {
    LiveEditLease stale = lease(notes, theirs, NOW.plusSeconds(60));
    rows(List.of(stale));

    assertThat(service.acquire(order, 1, mine.getId(), user, List.of(notes)))
        .isInstanceOf(Granted.class);
    assertThat(stale.getResourceGeneration()).isEqualTo(1);
  }

  @Test
  @DisplayName("L12: a lease never outlives its edit session; an ended session gets none")
  void sessionBoundsTheLease() {
    mine = session(user, Duration.ofSeconds(20));
    rows(List.of());
    Granted granted = (Granted) service.acquire(order, 0, mine.getId(), user, List.of(notes));
    assertThat(granted.leases().getFirst().getExpiresAt()).isEqualTo(NOW.plusSeconds(20));

    LiveEditSession ended = session(user, Duration.ofSeconds(-1));
    assertThatThrownBy(() -> service.acquire(order, 0, ended.getId(), user, List.of(notes)))
        .isInstanceOf(LiveEditSessionNotFoundException.class);
    // Another person's session is no session of this caller.
    assertThatThrownBy(() -> service.acquire(order, 0, theirs.getId(), user, List.of(notes)))
        .isInstanceOf(LiveEditSessionNotFoundException.class);
  }

  @Test
  @DisplayName("L14: with leases off nothing is granted and nothing is locked")
  void offGrantsNothing() {
    when(control.isEnforced(tenant, "sales-order")).thenReturn(false);

    assertThatThrownBy(() -> service.acquire(order, 0, mine.getId(), user, List.of(notes)))
        .isInstanceOf(LiveEditLeasesNotEnforcedException.class);
    verify(control, never()).lockResource(any(), any());
  }

  @Test
  @DisplayName("L19: the per-session bound refuses the grant as a whole")
  void boundsAreEnforced() {
    rows(List.of());
    when(leases.countHeldBySession(tenant, "sales-order", order.id(), mine.getId(), 0L, NOW))
        .thenReturn((long) properties.getMaxLeasesPerSession());

    assertThatThrownBy(() -> service.acquire(order, 0, mine.getId(), user, List.of(notes)))
        .isInstanceOf(LiveEditLeaseLimitException.class);
    verify(leases, never()).save(any());
  }

  @Test
  @DisplayName(
      "R2: the bounds count holdings of the current generation only; void rows of an older one do"
          + " not fill them, current ones still do")
  void boundsCountTheCurrentGeneration() {
    properties.setMaxLeasesPerSession(1);
    properties.setMaxLeasesPerResource(1);
    LiveEditLease voidRow = lease(notes, mine, NOW.plusSeconds(60));
    rows(List.of(voidRow));

    // Generation 1: the row of generation 0 is void and counted by nobody.
    Granted again = (Granted) service.acquire(order, 1, mine.getId(), user, List.of(notes));
    assertThat(again.leases().getFirst()).isSameAs(voidRow);
    verify(leases).countHeldBySession(tenant, "sales-order", order.id(), mine.getId(), 1L, NOW);
    verify(leases).countHeldOnResource(tenant, "sales-order", order.id(), 1L, NOW);

    // A real holding of the current generation still fills the resource bound.
    rows(List.of());
    when(leases.countHeldOnResource(tenant, "sales-order", order.id(), 1L, NOW)).thenReturn(1L);
    assertThatThrownBy(() -> service.acquire(order, 1, theirs.getId(), otherUser, List.of(terms)))
        .isInstanceOf(LiveEditLeaseLimitException.class);
  }

  @Test
  @DisplayName(
      "R3: a repeated acquire on an instance whose clock is behind keeps the token and never"
          + " writes a renewal before the acquisition")
  void repeatFromABehindClock() {
    LiveEditLease own =
        withId(
            LiveEditLease.grant(
                order, notes, 0, mine.getId(), user, NOW.plusSeconds(2), NOW.plusSeconds(92)));
    UUID token = own.getToken();
    rows(List.of(own));

    Granted again = (Granted) service.acquire(order, 0, mine.getId(), user, List.of(notes));

    assertThat(again.leases().getFirst().getToken()).isEqualTo(token);
    assertThat(own.getRenewedAt()).isAfterOrEqualTo(own.getAcquiredAt());
    assertThat(own.getExpiresAt()).isAfter(own.getRenewedAt());
    assertThat(own.getExpiresAt()).isBeforeOrEqualTo(mine.getExpiresAt());
  }

  @Test
  @DisplayName(
      "R3 follow-up: a renewal with no consistent extension ends the period on the server and"
          + " reports its token lost")
  void inconsistentRenewalEndsThePeriod() {
    // Granted by an instance 100 s ahead; the session was since renewed on this clock, so it ends
    // before the lease's recorded renewal time.
    mine = session(user, Duration.ofSeconds(20));
    LiveEditLease own =
        withId(
            LiveEditLease.grant(
                order, notes, 0, mine.getId(), user, NOW.plusSeconds(100), NOW.plusSeconds(190)));
    UUID token = own.getToken();
    when(leases.lockByTokens(eq(tenant), eq("sales-order"), eq(order.id()), anyCollection()))
        .thenReturn(List.of(own));

    LiveEditLeaseService.Renewal renewal =
        service.renew(order, 0, mine.getId(), user, List.of(token));

    assertThat(renewal.renewed()).isEmpty();
    assertThat(renewal.lost()).containsExactly(token);
    assertThat(own.getReleasedAt()).isNotNull();
    assertThat(own.isHeldAt(NOW, 0)).isFalse();
    assertThat(own.getReleasedAt()).isAfterOrEqualTo(own.getRenewedAt());

    // The token proves nothing any more, and the key is free for anybody.
    rows(List.of(own));
    Verification stale =
        service.verify(
            order, 0, LiveLeaseMode.ENFORCED, mine.getId(), Set.of(token), user, Set.of(notes));
    assertThat(stale.proven()).isFalse();
    assertThat(stale.missing()).singleElement().satisfies(m -> assertThat(m.holder()).isNull());
    Granted taken = (Granted) service.acquire(order, 0, theirs.getId(), otherUser, List.of(notes));
    assertThat(taken.leases().getFirst().getToken()).isNotEqualTo(token);
    assertThat(own.getSessionId()).isEqualTo(theirs.getId());
  }

  @Test
  @DisplayName(
      "R3 follow-up: a repeated acquire with no consistent extension starts a new period with a"
          + " new token; the old one is void")
  void inconsistentRepeatStartsANewPeriod() {
    mine = session(user, Duration.ofSeconds(20));
    LiveEditLease own =
        withId(
            LiveEditLease.grant(
                order, notes, 0, mine.getId(), user, NOW.plusSeconds(100), NOW.plusSeconds(190)));
    UUID old = own.getToken();
    rows(List.of(own));

    Granted again = (Granted) service.acquire(order, 0, mine.getId(), user, List.of(notes));

    LiveEditLease lease = again.leases().getFirst();
    assertThat(lease).isSameAs(own);
    assertThat(lease.getToken()).isNotEqualTo(old);
    assertThat(lease.getSessionId()).isEqualTo(mine.getId());
    assertThat(lease.getAcquiredAt()).isEqualTo(NOW);
    assertThat(lease.getRenewedAt()).isEqualTo(NOW);
    assertThat(lease.getExpiresAt()).isEqualTo(NOW.plusSeconds(20));
    assertThat(lease.getReleasedAt()).isNull();
    assertThat(lease.isHeldBy(mine.getId(), NOW, 0)).isTrue();

    Verification withOld =
        service.verify(
            order, 0, LiveLeaseMode.ENFORCED, mine.getId(), Set.of(old), user, Set.of(notes));
    assertThat(withOld.proven()).isFalse();
    assertThat(withOld.unexpectedTokens()).containsExactly(old);
  }

  @Test
  @DisplayName("L05: renewal extends only this session's held leases; other tokens are lost")
  void renewalIsPerLease() {
    LiveEditLease own = lease(notes, mine, NOW.plusSeconds(10));
    LiveEditLease foreign = lease(terms, theirs, NOW.plusSeconds(10));
    LiveEditLease ended = lease(new LiveLeaseKey(HEADER, "deadline"), mine, NOW.minusSeconds(1));
    when(leases.lockByTokens(eq(tenant), eq("sales-order"), eq(order.id()), anyCollection()))
        .thenReturn(List.of(ended, own, foreign));

    LiveEditLeaseService.Renewal renewal =
        service.renew(
            order,
            0,
            mine.getId(),
            user,
            List.of(own.getToken(), foreign.getToken(), ended.getToken()));

    assertThat(renewal.renewed()).containsExactly(own);
    assertThat(own.getExpiresAt()).isEqualTo(NOW.plusSeconds(90));
    assertThat(renewal.lost()).containsExactlyInAnyOrder(foreign.getToken(), ended.getToken());
    assertThat(foreign.getExpiresAt()).isEqualTo(NOW.plusSeconds(10));
  }

  @Test
  @DisplayName("L05: a release names the period: another session's lease is never released")
  void releaseIsScoped() {
    LiveEditLease own = lease(notes, mine, NOW.plusSeconds(10));
    LiveEditLease foreign = lease(terms, theirs, NOW.plusSeconds(10));
    when(leases.lockByTokens(eq(tenant), eq("sales-order"), eq(order.id()), anyCollection()))
        .thenReturn(List.of(own, foreign));

    int released =
        service.release(order, mine.getId(), user, List.of(own.getToken(), foreign.getToken()));

    assertThat(released).isEqualTo(1);
    assertThat(own.getReleasedAt()).isEqualTo(NOW);
    assertThat(foreign.getReleasedAt()).isNull();
  }

  @Test
  @DisplayName("L08/L11: enforced, a save without the token of a key it writes misses that key")
  void enforcedVerification() {
    LiveEditLease own = lease(notes, mine, NOW.plusSeconds(30));
    LiveEditLease other = lease(terms, theirs, NOW.plusSeconds(30));
    rows(List.of(own, other));

    Verification proven =
        service.verify(
            order,
            0,
            LiveLeaseMode.ENFORCED,
            mine.getId(),
            Set.of(own.getToken()),
            user,
            Set.of(notes));
    assertThat(proven.proven()).isTrue();
    assertThat(proven.held()).containsExactly(own);

    Verification noToken =
        service.verify(
            order, 0, LiveLeaseMode.ENFORCED, mine.getId(), Set.of(), user, Set.of(notes));
    assertThat(noToken.missing())
        .extracting(LiveEditLeaseService.Missing::key)
        .containsExactly(notes);

    Verification blocked =
        service.verify(
            order,
            0,
            LiveLeaseMode.ENFORCED,
            mine.getId(),
            Set.of(own.getToken()),
            user,
            Set.of(notes, terms));
    assertThat(blocked.held()).containsExactly(own);
    assertThat(blocked.missing())
        .singleElement()
        .satisfies(m -> assertThat(m.holder()).isSameAs(other));

    UUID surplus = UUID.randomUUID();
    Verification extra =
        service.verify(
            order,
            0,
            LiveLeaseMode.ENFORCED,
            mine.getId(),
            Set.of(own.getToken(), surplus),
            user,
            Set.of(notes));
    assertThat(extra.missing()).isEmpty();
    assertThat(extra.unexpectedTokens()).containsExactly(surplus);
  }

  @Test
  @DisplayName("L06: a lease that expires while the save waits is not held after the wait")
  void timeIsReadAfterTheLocks() {
    LiveEditLease own = lease(notes, mine, NOW);
    rows(List.of(own));

    Verification late =
        service.verify(
            order,
            0,
            LiveLeaseMode.ENFORCED,
            mine.getId(),
            Set.of(own.getToken()),
            user,
            Set.of(notes));
    assertThat(late.missing()).hasSize(1);
  }

  @Test
  @DisplayName("L14/L17: off, no proof is needed, but a key somebody still holds is refused")
  void offStillHonoursHeldLeases() {
    rows(List.of());
    assertThat(
            service
                .verify(order, 0, LiveLeaseMode.OFF, null, Set.of(), user, Set.of(notes))
                .proven())
        .isTrue();

    LiveEditLease leftOver = lease(notes, theirs, NOW.plusSeconds(30));
    rows(List.of(leftOver));
    Verification refused =
        service.verify(order, 0, LiveLeaseMode.OFF, null, Set.of(), user, Set.of(notes));
    assertThat(refused.missing())
        .singleElement()
        .satisfies(m -> assertThat(m.holder()).isSameAs(leftOver));
  }

  @Test
  @DisplayName("L18: the lease marker changes with grant and release, not with renewal")
  void digestIgnoresRenewal() {
    LiveEditLease lease = lease(notes, mine, NOW.plusSeconds(30));
    LiveRevision before = LiveEditLeaseService.digest(List.of(lease));
    assertThat(before.value()).startsWith("l");
    assertThat(before.value()).doesNotContain(lease.getToken().toString());

    lease.renew(NOW, NOW.plusSeconds(90), NOW.plusSeconds(600), 0);
    assertThat(LiveEditLeaseService.digest(List.of(lease))).isEqualTo(before);
    assertThat(LiveEditLeaseService.digest(List.of())).isEqualTo(new LiveRevision("0"));
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private LiveEditSession session(UUID owner, Duration lifetime) {
    LiveEditSession session =
        Duration.ZERO.compareTo(lifetime) < 0
            ? LiveEditSession.open(order, owner, NOW, lifetime)
            : LiveEditSession.open(
                order, owner, NOW.plus(lifetime).minusSeconds(60), Duration.ofSeconds(60));
    session.setId(UUID.randomUUID());
    session.setTenantId(tenant);
    when(sessions.lockForLease(tenant, session.getId(), "sales-order", order.id()))
        .thenReturn(Optional.of(session));
    return session;
  }

  private LiveEditLease lease(LiveLeaseKey key, LiveEditSession holder, Instant expiresAt) {
    Instant acquired = expiresAt.minusSeconds(90);
    LiveEditLease lease =
        LiveEditLease.grant(order, key, 0, holder.getId(), holder.getUserId(), acquired, expiresAt);
    return withId(lease);
  }

  private static LiveEditLease withId(LiveEditLease lease) {
    if (lease.getId() == null) {
      lease.setId(UUID.randomUUID());
    }
    return lease;
  }

  private void rows(List<LiveEditLease> rows) {
    when(leases.lockInScopes(eq(tenant), eq("sales-order"), eq(order.id()), any()))
        .thenReturn(new ArrayList<>(rows));
  }
}
