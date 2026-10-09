package com.fabricmanagement.platform.realtime.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.realtime.domain.LiveEditSession;
import com.fabricmanagement.platform.realtime.domain.LiveResource;
import com.fabricmanagement.platform.realtime.domain.LiveRevision;
import com.fabricmanagement.platform.realtime.domain.exception.LiveEditSessionNotFoundException;
import com.fabricmanagement.platform.realtime.infra.repository.LiveEditSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Edit sessions (CEDIT-06 §2): lifetime, own-session renewal and the presence marker. */
class LiveEditSessionServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

  private final UUID tenant = UUID.randomUUID();
  private final UUID user = UUID.randomUUID();
  private final LiveResource order = new LiveResource("sales-order", UUID.randomUUID());
  private final LiveEditSessionRepository repository = mock(LiveEditSessionRepository.class);
  private final LiveEditLeaseService leases = mock(LiveEditLeaseService.class);
  private LiveEditSessionProperties properties;
  private LiveEditSessionService service;

  @BeforeEach
  void setUp() {
    properties = new LiveEditSessionProperties();
    properties.afterPropertiesSet();
    service =
        new LiveEditSessionService(
            repository, properties, leases, Clock.fixed(NOW, ZoneOffset.UTC));
    TenantContext.setCurrentTenantId(tenant);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("S01: a session opens now, for the configured lifetime, on the named resource")
  void opensForTheLifetime() {
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    LiveEditSession session = service.open(order, user);

    assertThat(session.getResourceType()).isEqualTo("sales-order");
    assertThat(session.getResourceId()).isEqualTo(order.id());
    assertThat(session.getUserId()).isEqualTo(user);
    assertThat(session.getOpenedAt()).isEqualTo(NOW);
    assertThat(session.getExpiresAt()).isEqualTo(NOW.plusSeconds(90));
    assertThat(session.isLiveAt(NOW.plusSeconds(89))).isTrue();
    assertThat(session.isLiveAt(NOW.plusSeconds(90))).isFalse();
    assertThat(service.renewAfterSeconds()).isEqualTo(30);
  }

  @Test
  @DisplayName("S02: renewal extends only the user's own open session; anything else is unknown")
  void renewalIsConditional() {
    UUID id = UUID.randomUUID();
    when(repository.renew(tenant, id, "sales-order", order.id(), user, NOW, NOW.plusSeconds(90)))
        .thenReturn(1);
    assertThat(service.renew(order, id, user)).isEqualTo(NOW.plusSeconds(90));

    UUID other = UUID.randomUUID();
    assertThatThrownBy(() -> service.renew(order, other, user))
        .isInstanceOf(LiveEditSessionNotFoundException.class)
        .satisfies(
            failure ->
                assertThat(((LiveEditSessionNotFoundException) failure).getHttpStatus())
                    .isEqualTo(404));
  }

  @Test
  @DisplayName(
      "S02b: a sub-microsecond clock answers the times PostgreSQL keeps, on open and on renewal")
  void timesAreKeptAtDatabasePrecision() {
    Instant fine = NOW.plusNanos(704_264_232);
    Instant kept = NOW.plusNanos(704_264_000);
    LiveEditSessionService onLinux =
        new LiveEditSessionService(
            repository, properties, leases, Clock.fixed(fine, ZoneOffset.UTC));
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    LiveEditSession session = onLinux.open(order, user);

    assertThat(session.getOpenedAt()).isEqualTo(kept);
    assertThat(session.getExpiresAt()).isEqualTo(kept.plusSeconds(90));
    UUID id = UUID.randomUUID();
    when(repository.renew(tenant, id, "sales-order", order.id(), user, kept, kept.plusSeconds(90)))
        .thenReturn(1);
    assertThat(onLinux.renew(order, id, user)).isEqualTo(kept.plusSeconds(90));
  }

  @Test
  @DisplayName("S03: closing names the tenant, resource and user; it never throws")
  void closingIsScoped() {
    UUID id = UUID.randomUUID();
    service.close(order, id, user);
    verify(repository).close(tenant, id, "sales-order", order.id(), user, NOW);
    // Nothing was closed (already closed, or not the user's): no lease is touched.
    org.mockito.Mockito.verifyNoInteractions(leases);
  }

  @Test
  @DisplayName("CEDIT-07 L12: closing my session ends its leases, after the session row")
  void closingEndsTheSessionsLeases() {
    UUID id = UUID.randomUUID();
    when(repository.close(tenant, id, "sales-order", order.id(), user, NOW)).thenReturn(1);

    service.close(order, id, user);

    org.mockito.InOrder sequence = org.mockito.Mockito.inOrder(repository, leases);
    sequence.verify(repository).close(tenant, id, "sales-order", order.id(), user, NOW);
    sequence.verify(leases).releaseSession(order, id, user);
  }

  @Test
  @DisplayName("S04: the presence marker is 0 for nobody and changes with the set, not its order")
  void presenceMarker() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();

    assertThat(LiveEditSessionService.digest(List.of())).isEqualTo(new LiveRevision("0"));
    LiveRevision ab = LiveEditSessionService.digest(List.of(a, b));
    assertThat(ab.value()).startsWith("p").hasSize(33);
    assertThat(LiveEditSessionService.digest(List.of(b, a))).isEqualTo(ab);
    assertThat(LiveEditSessionService.digest(List.of(a))).isNotEqualTo(ab);
    assertThat(LiveEditSessionService.digest(List.of(a, b, c))).isNotEqualTo(ab);
    assertThat(ab.value()).doesNotContain(a.toString()).doesNotContain(b.toString());

    when(repository.findLiveIds(tenant, "sales-order", order.id(), NOW)).thenReturn(List.of(a, b));
    assertThat(service.presenceRevision(order)).isEqualTo(ab);
  }

  @Test
  @DisplayName("S05: nothing runs without a bound tenant")
  void requiresTenant() {
    TenantContext.clear();
    assertThatThrownBy(() -> service.live(order)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> service.presenceRevision(order)).isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("S06: the renewal interval must be shorter than the lifetime")
  void renewalInsideLifetime() {
    LiveEditSessionProperties invalid = new LiveEditSessionProperties();
    invalid.setRenewAfter(Duration.ofSeconds(90));
    assertThatThrownBy(invalid::afterPropertiesSet).isInstanceOf(IllegalStateException.class);
  }
}
