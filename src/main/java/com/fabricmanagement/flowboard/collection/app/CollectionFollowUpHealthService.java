package com.fabricmanagement.flowboard.collection.app;

import com.fabricmanagement.flowboard.collection.app.port.CollectionFollowUpPort;
import com.fabricmanagement.flowboard.collection.domain.CollectionFollowUpScan;
import com.fabricmanagement.flowboard.collection.infra.repository.CollectionFollowUpScanRepository;
import java.time.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CollectionFollowUpHealthService {
  private final CollectionFollowUpScanRepository scans;
  private final Clock clock;
  private final CollectionFollowUpPort source;

  @Transactional
  public void finish(UUID tenant, boolean failed) {
    source.lock();
    var state = scans.findByTenantId(tenant).orElseGet(CollectionFollowUpScan::create);
    state.finish(clock.instant(), failed);
    scans.saveAndFlush(state);
  }

  @Transactional(readOnly = true)
  public boolean stale(UUID tenant) {
    return scans
            .findByTenantId(tenant)
            .map(
                s ->
                    s.isFailed()
                        || s.getCompletedAt() == null
                        || s.getCompletedAt()
                            .isBefore(clock.instant().minus(Duration.ofMinutes(10))))
            .orElse(true)
        || source.upstreamStale();
  }
}
