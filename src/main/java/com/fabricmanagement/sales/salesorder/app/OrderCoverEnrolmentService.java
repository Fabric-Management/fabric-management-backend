package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesorder.domain.*;
import com.fabricmanagement.sales.salesorder.domain.event.OrderCoverCaseOpenedEvent;
import com.fabricmanagement.sales.salesorder.infra.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderCoverEnrolmentService {
  private final OrderCoverActivationRepository activations;
  private final OrderCoverCaseRepository cases;
  private final OrderCoverCaseLineRepository caseLines;
  private final DomainEventPublisher events;

  @Transactional(propagation = Propagation.MANDATORY)
  public OrderCoverRegime decide(SalesOrder order, List<SalesOrderLine> lines) {
    if (order.getCoverRegime() != null) return order.getCoverRegime();
    UUID tenantId = TenantContext.requireTenantId();
    OrderCoverRegime regime =
        activations
            .find(tenantId)
            .filter(a -> order.getCreationSeq() != null && order.getCreationSeq() > a.boundarySeq())
            .map(a -> OrderCoverRegime.GOVERNED)
            .orElse(OrderCoverRegime.LEGACY);
    order.decideCoverRegime(regime);
    if (regime == OrderCoverRegime.GOVERNED) {
      OrderCoverCase coverCase = cases.saveAndFlush(OrderCoverCase.open(tenantId, order.getId()));
      Set<UUID> unresolved = new LinkedHashSet<>();
      for (SalesOrderLine line : lines) {
        unresolved.add(line.getId());
        caseLines.save(OrderCoverCaseLine.unresolved(tenantId, coverCase.getId(), line.getId()));
      }
      events.publish(
          new OrderCoverCaseOpenedEvent(
              tenantId,
              coverCase.getId(),
              order.getId(),
              order.getOrderNumber(),
              order.getDeadline(),
              unresolved));
    }
    return regime;
  }
}
