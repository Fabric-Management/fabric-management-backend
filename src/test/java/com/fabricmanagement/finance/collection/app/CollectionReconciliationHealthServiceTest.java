package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.domain.CollectionReconciliationHealth;
import com.fabricmanagement.finance.collection.infra.repository.CollectionReconciliationHealthRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class CollectionReconciliationHealthServiceTest {
  final UUID tenant = UUID.randomUUID();
  final Instant now = Instant.parse("2026-10-22T12:00:00Z");
  final CollectionReconciliationHealthRepository repo =
      mock(CollectionReconciliationHealthRepository.class);
  final CollectionCommandStore commands = mock(CollectionCommandStore.class);
  final CollectionReconciliationHealthService service =
      new CollectionReconciliationHealthService(repo, commands, Clock.fixed(now, ZoneOffset.UTC));

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void absentFailedAndOverdueDiscoveryCannotCertifyCoverage() {
    when(repo.findByTenantId(tenant)).thenReturn(Optional.empty());
    assertThat(service.stale()).isTrue();
    var state = CollectionReconciliationHealth.create();
    state.finish(now, false);
    when(repo.findByTenantId(tenant)).thenReturn(Optional.of(state));
    assertThat(service.stale()).isFalse();
    state.finish(now, true);
    assertThat(service.stale()).isTrue();
    assertThat(state.getCompletedAt()).isEqualTo(now);
    state.finish(now.minusSeconds(601), false);
    assertThat(service.stale()).isTrue();
  }

  @Test
  void successfulEmptyScanRecordsCoverageOnlyAfterDiscoveryCompleted() {
    when(repo.findByTenantId(tenant)).thenReturn(Optional.empty());
    service.finish(false);
    verify(commands).lock();
    verify(repo)
        .saveAndFlush(
            org.mockito.ArgumentMatchers.argThat(
                s -> !s.isFailed() && now.equals(s.getCompletedAt())));
  }
}
