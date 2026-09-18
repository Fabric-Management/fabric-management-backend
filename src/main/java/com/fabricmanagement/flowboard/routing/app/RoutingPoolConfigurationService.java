package com.fabricmanagement.flowboard.routing.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.flowboard.routing.domain.*;
import com.fabricmanagement.flowboard.routing.domain.RoutingRecords.Pool;
import com.fabricmanagement.flowboard.routing.domain.event.RoutingPoolChangedEvent;
import com.fabricmanagement.flowboard.routing.domain.exception.*;
import com.fabricmanagement.flowboard.routing.infra.repository.RoutingRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class RoutingPoolConfigurationService {
  private final RoutingRepository repository;
  private final RoutingEligibilityService eligibility;
  private final DomainEventPublisher events;

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Pool configure(UUID tenant, RoutingPoolKey key, Long expectedRevision, Set<UUID> members) {
    repository.lockPool(tenant, key, true);
    Pool current = repository.pool(tenant, key).orElse(null);
    if (current == null
        ? expectedRevision != null
        : expectedRevision == null || current.revision() != expectedRevision) {
      throw new RoutingException("Routing pool revision changed", "ROUTING_REVISION_CONFLICT", 409);
    }
    Map<UUID, List<RoutingReason>> invalid = new LinkedHashMap<>();
    members.stream()
        .sorted()
        .forEach(
            id -> {
              var reasons = eligibility.candidacy(tenant, eligibility.user(tenant, id));
              if (!reasons.isEmpty()) invalid.put(id, reasons);
            });
    if (!invalid.isEmpty()) throw new RoutingMembersRejectedException(invalid);
    Pool saved = repository.configure(tenant, key, current, members);
    events.publish(new RoutingPoolChangedEvent(tenant, key, saved.revision()));
    return saved;
  }
}
