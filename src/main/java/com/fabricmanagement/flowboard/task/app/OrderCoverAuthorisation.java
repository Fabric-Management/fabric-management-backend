package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.flowboard.routing.app.RoutingEligibilityService;
import com.fabricmanagement.flowboard.routing.domain.RoutingPoolKey;
import com.fabricmanagement.flowboard.routing.domain.RoutingReason;
import com.fabricmanagement.flowboard.routing.infra.repository.RoutingRepository;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.infra.repository.TaskAssigneeRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * First-execution settlement gate (FE-ARCH-5b-3 §3.1, ADR-0003 D48).
 *
 * <p>Both action grants, the active flag and the sales write object scope are evaluated through
 * {@link RoutingEligibilityService}, which reads the actor uncached. There is deliberately no
 * second permission lookup here: a reverse "every user holding this action" query would scale with
 * the tenant and could only repeat, or contradict, the fresh answer.
 */
@Component
@RequiredArgsConstructor
public class OrderCoverAuthorisation {
  private final RoutingRepository routing;
  private final RoutingEligibilityService eligibility;
  private final TaskAssigneeRepository assignees;

  @Transactional(propagation = Propagation.MANDATORY)
  public void assertFirstExecution(Task task, UUID actorId) {
    UUID tenantId = TenantContext.requireTenantId();
    Long taskVersion = task.getVersion();
    // Shared pool lock first: pool configuration takes it exclusively, so a concurrent member
    // removal and this settlement commit in a defined order (§3.2).
    routing.lockPool(tenantId, RoutingPoolKey.ORDER_COVER, false);
    var user = eligibility.user(tenantId, actorId);
    var pool = routing.pool(tenantId, RoutingPoolKey.ORDER_COVER).orElse(null);
    boolean member =
        pool != null
            && routing.members(tenantId, pool.id()).stream()
                .anyMatch(candidate -> candidate.active() && candidate.userId().equals(actorId));
    List<RoutingReason> reasons =
        eligibility.eligibility(tenantId, task.getEntityId(), user, member);
    if (reasons.stream().anyMatch(reason -> reason != RoutingReason.NOT_A_MEMBER)) {
      throw new DecisionBlockedException(
          "PERMISSION_DENIED", requiredPermission(reasons), taskVersion);
    }
    if (reasons.contains(RoutingReason.NOT_A_MEMBER)) {
      throw new DecisionBlockedException("OUTSIDE_ROUTING_POOL", null, taskVersion);
    }
    var active = assignees.findAllByTaskIdAndIsActiveTrue(task.getId());
    if (active.isEmpty()) {
      throw new DecisionBlockedException("UNASSIGNED", null, taskVersion);
    }
    if (active.stream().noneMatch(assignee -> assignee.getUserId().equals(actorId))) {
      throw new DecisionBlockedException("ASSIGNED_ELSEWHERE", null, taskVersion);
    }
  }

  /** The grant whose absence blocked the actor, when the reason identifies one. */
  private static String requiredPermission(List<RoutingReason> reasons) {
    if (reasons.contains(RoutingReason.MISSING_FLOWBOARD_WRITE)) {
      return PermissionKey.FLOWBOARD_WRITE.key();
    }
    if (reasons.contains(RoutingReason.MISSING_SALES_WRITE)
        || reasons.contains(RoutingReason.OUTSIDE_SALES_WRITE_SCOPE)) {
      return PermissionKey.SALES_WRITE.key();
    }
    return null;
  }
}
