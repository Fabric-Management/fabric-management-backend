package com.fabricmanagement.platform.realtime.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.realtime.domain.*;
import com.fabricmanagement.platform.realtime.domain.exception.LiveEditLeaseLimitException;
import com.fabricmanagement.platform.realtime.infra.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class LiveEditLeaseReplacementTest {
  final UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
  final LiveResource resource = new LiveResource("stock-product", UUID.randomUUID());
  final Instant now = Instant.parse("2026-10-19T09:00:00Z");
  final LiveEditLeaseRepository rows = mock(LiveEditLeaseRepository.class);
  final LiveEditSessionRepository sessions = mock(LiveEditSessionRepository.class);
  final LiveEditLeaseControlRepository control = mock(LiveEditLeaseControlRepository.class);
  final LiveLeaseKey oldKey = LiveLeaseKey.whole("stock-unit:" + UUID.randomUUID());
  final LiveLeaseKey newKey = LiveLeaseKey.whole("stock-unit:" + UUID.randomUUID());
  final List<LiveEditLease> stored = new ArrayList<>();
  LiveEditLeaseService service;
  LiveEditSession mine;
  LiveEditLease previous;

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    mine = LiveEditSession.open(resource, actor, now, Duration.ofMinutes(5));
    mine.setId(UUID.randomUUID());
    previous =
        LiveEditLease.grant(resource, oldKey, 1, mine.getId(), actor, now, now.plusSeconds(90));
    stored.add(previous);
    service =
        new LiveEditLeaseService(
            rows,
            sessions,
            control,
            new LiveEditLeaseProperties(),
            Clock.fixed(now, ZoneOffset.UTC));
    when(sessions.lockForLease(
            eq(tenant), eq(mine.getId()), eq(resource.type()), eq(resource.id())))
        .thenReturn(Optional.of(mine));
    when(rows.lockInScopes(any(), any(), any(), any())).thenAnswer(call -> List.copyOf(stored));
    when(rows.findHeld(any(), any(), any(), anyLong(), any()))
        .thenAnswer(call -> stored.stream().filter(row -> row.isHeldAt(now, 1)).toList());
    when(rows.save(any()))
        .thenAnswer(
            call -> {
              LiveEditLease row = call.getArgument(0);
              stored.add(row);
              return row;
            });
    when(rows.lockByTokens(any(), any(), any(), any()))
        .thenAnswer(
            call -> {
              Collection<UUID> tokens = call.getArgument(3);
              return stored.stream().filter(row -> tokens.contains(row.getToken())).toList();
            });
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void successfulReplacementRetainsNewPeriodAndReleasesOnlyRemovedKeys() {
    var granted =
        (LiveEditLeaseService.Granted)
            service.replace(resource, 1, mine.getId(), actor, List.of(newKey));
    assertThat(previous.getReleasedAt()).isEqualTo(now);
    assertThat(granted.leases())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.key()).isEqualTo(newKey);
              assertThat(row.getReleasedAt()).isNull();
            });
  }

  @Test
  void refusedReplacementDoesNotReleaseTheOldSet() {
    var other =
        LiveEditLease.grant(
            resource, newKey, 1, UUID.randomUUID(), UUID.randomUUID(), now, now.plusSeconds(90));
    stored.add(other);
    assertThat(service.replace(resource, 1, mine.getId(), actor, List.of(newKey)))
        .isInstanceOf(LiveEditLeaseService.Refused.class);
    assertThat(previous.getReleasedAt()).isNull();
    verify(rows, never()).save(any());
    verify(rows, never()).lockByTokens(any(), any(), any(), any());
  }

  @Test
  void aggregateLimitDoesNotBecomeASequenceOfPartialAcquires() {
    var keys =
        java.util.stream.IntStream.range(0, 51)
            .mapToObj(index -> LiveLeaseKey.whole("stock-unit:" + UUID.randomUUID()))
            .toList();
    assertThatThrownBy(() -> service.replace(resource, 1, mine.getId(), actor, keys))
        .isInstanceOf(LiveEditLeaseLimitException.class);
    assertThat(previous.getReleasedAt()).isNull();
    verify(rows, never()).save(any());
  }
}
