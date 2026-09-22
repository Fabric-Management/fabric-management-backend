package com.fabricmanagement.flowboard.task.app.adapter;

import com.fabricmanagement.flowboard.routing.app.RoutingEligibilityService;
import com.fabricmanagement.flowboard.routing.domain.*;
import com.fabricmanagement.flowboard.routing.infra.repository.RoutingRepository;
import com.fabricmanagement.flowboard.task.domain.OrderCoverActionEvaluator;
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

  public Snapshot evaluate(
      UUID tenant,
      UUID orderId,
      UUID taskId,
      UUID actor,
      boolean caseOpen,
      boolean actionableEvidence) {
    if (!caseOpen) return new Snapshot(null, null, List.of(), false, "CASE_CLOSED");
    if (taskId == null) return new Snapshot(null, null, List.of(), false, "UNASSIGNED");
    var task = tasks.findById(taskId).filter(t -> tenant.equals(t.getTenantId())).orElse(null);
    if (task == null) return new Snapshot(null, null, List.of(), false, "CASE_CLOSED");
    var assignments = assignees.findAllByTaskIdAndIsActiveTrue(taskId);
    List<UUID> direct =
        assignments.stream().map(a -> a.getUserId()).filter(Objects::nonNull).toList();
    var user = eligibility.user(tenant, actor);
    var candidacy = eligibility.candidacy(tenant, user);
    boolean candidate = candidacy.isEmpty();
    boolean member = candidate && routing.isActiveMember(tenant, RoutingPoolKey.ORDER_COVER, actor);
    boolean writeScopeAllowed =
        candidate && member && eligibility.writeScopeAllowed(tenant, orderId, actor);
    var result =
        OrderCoverActionEvaluator.evaluate(
            new OrderCoverActionEvaluator.Inputs(
                true,
                true,
                candidate,
                member,
                writeScopeAllowed,
                !assignments.isEmpty(),
                direct,
                actor,
                actionableEvidence));
    return new Snapshot(
        task.getVersion(),
        task.getStatus().name(),
        direct,
        result.allowed(),
        result.blockedReason());
  }
}
