package com.fabricmanagement.flowboard.task.app.adapter;

import com.fabricmanagement.flowboard.routing.app.RoutingEligibilityService;
import com.fabricmanagement.flowboard.routing.domain.*;
import com.fabricmanagement.flowboard.routing.infra.repository.RoutingRepository;
import com.fabricmanagement.flowboard.task.infra.repository.*;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverCapabilityPort;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCoverCapabilityAdapter implements OrderCoverCapabilityPort {
  private final TaskRepository tasks;
  private final TaskAssigneeRepository assignees;
  private final RoutingRepository routing;
  private final RoutingEligibilityService eligibility;

  public Snapshot evaluate(UUID tenant, UUID orderId, UUID taskId, UUID actor) {
    var task = tasks.findById(taskId).filter(t -> tenant.equals(t.getTenantId())).orElse(null);
    if (task == null) return new Snapshot(null, null, List.of(), false, "CASE_CLOSED");
    List<UUID> direct =
        assignees.findAllByTaskIdAndIsActiveTrue(taskId).stream().map(a -> a.getUserId()).toList();
    var pool = routing.pool(tenant, RoutingPoolKey.ORDER_COVER).orElse(null);
    boolean member =
        pool != null
            && routing.members(tenant, pool.id()).stream()
                .anyMatch(m -> m.active() && m.userId().equals(actor));
    var user = eligibility.user(tenant, actor);
    var reasons = eligibility.eligibility(tenant, orderId, user, member);
    String blocked =
        reasons.stream().anyMatch(reason -> reason != RoutingReason.NOT_A_MEMBER)
            ? "PERMISSION_DENIED"
            : reasons.contains(RoutingReason.NOT_A_MEMBER)
                ? "OUTSIDE_ROUTING_POOL"
                : direct.isEmpty()
                    ? "UNASSIGNED"
                    : !direct.contains(actor) ? "ASSIGNED_ELSEWHERE" : null;
    return new Snapshot(
        task.getVersion(), task.getStatus().name(), direct, blocked == null, blocked);
  }
}
