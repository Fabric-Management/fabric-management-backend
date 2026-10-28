package com.fabricmanagement.flowboard.collection.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.flowboard.collection.app.port.CollectionFollowUpPort;
import com.fabricmanagement.flowboard.collection.domain.CollectionFollowUpScan;
import com.fabricmanagement.flowboard.collection.infra.repository.CollectionFollowUpScanRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CollectionFollowUpHealthTest {
  @Test
  void downstreamEmptyScanCannotHideUnavailableUpstreamDiscovery() {
    UUID tenant = UUID.randomUUID();
    Instant now = Instant.parse("2026-10-22T12:00:00Z");
    var repo = mock(CollectionFollowUpScanRepository.class);
    var source = mock(CollectionFollowUpPort.class);
    var healthy = CollectionFollowUpScan.create();
    healthy.finish(now, false);
    when(repo.findByTenantId(tenant)).thenReturn(Optional.of(healthy));
    when(source.upstreamStale()).thenReturn(true);
    var service =
        new CollectionFollowUpHealthService(repo, Clock.fixed(now, ZoneOffset.UTC), source);
    assertThat(service.stale(tenant)).isTrue();
    when(source.upstreamStale()).thenReturn(false);
    assertThat(service.stale(tenant)).isFalse();
  }
}
