package com.fabricmanagement.flowboard.routing.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.routing.domain.*;
import com.fabricmanagement.flowboard.routing.domain.RoutingRecords.*;
import com.fabricmanagement.flowboard.routing.domain.event.RoutingFailureOpenedEvent;
import com.fabricmanagement.flowboard.routing.domain.exception.RoutingException;
import com.fabricmanagement.flowboard.routing.domain.port.in.GovernedTaskRoutingPort;
import com.fabricmanagement.flowboard.routing.infra.repository.RoutingRepository;
import com.fabricmanagement.flowboard.task.domain.*;
import com.fabricmanagement.flowboard.task.infra.repository.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoutingEvaluationService implements GovernedTaskRoutingPort {
  private final RoutingRepository repository;
  private final RoutingEligibilityService eligibility;
  private final TaskRepository tasks;
  private final TaskVersionLockRepository taskVersionLocks;
  private final TaskAssigneeRepository assignees;
  private final DomainEventPublisher events;

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Evaluation evaluate(UUID tenantId, RoutingPoolKey poolKey, UUID taskId) {
    if (!tenantId.equals(TenantContext.requireTenantId())) {
      throw new RoutingException("Task not found", "ROUTING_TASK_NOT_FOUND", 404);
    }
    repository.lockPool(tenantId, poolKey, false);
    repository.lockState(tenantId, taskId, poolKey);
    Pool pool = repository.pool(tenantId, poolKey).orElse(null);
    Task task =
        tasks
            .findById(taskId)
            .filter(t -> tenantId.equals(t.getTenantId()))
            .orElseThrow(
                () -> new RoutingException("Task not found", "ROUTING_TASK_NOT_FOUND", 404));
    if (!task.getTaskType().name().equals(poolKey.name())
        || task.getWorkflowDefinitionId() == null
        || !"SALES_ORDER".equals(task.getEntityType())
        || task.getEntityId() == null) {
      throw new RoutingException(
          "Task is not governed by this routing pool", "ROUTING_TASK_NOT_GOVERNED", 422);
    }
    if (!Boolean.TRUE.equals(task.getIsActive()) || !task.isOpen() || task.getClosedAt() != null) {
      return new Evaluation(false, 0, 0);
    }
    Set<UUID> desired = new LinkedHashSet<>();
    Set<Condition> conditions = new LinkedHashSet<>();
    Long revision = pool == null ? null : pool.revision();
    if (pool == null) {
      conditions.add(new Condition(RoutingFailureReason.NO_POOL, null));
    } else {
      var members =
          repository.members(tenantId, pool.id()).stream().filter(Member::active).toList();
      if (members.isEmpty()) conditions.add(new Condition(RoutingFailureReason.POOL_EMPTY, null));
      for (var member : members) {
        var user = eligibility.user(tenantId, member.userId());
        if (eligibility.eligibility(tenantId, task.getEntityId(), user, true).isEmpty()) {
          desired.add(member.userId());
        } else {
          conditions.add(new Condition(RoutingFailureReason.RECIPIENT_INVALID, member.userId()));
        }
      }
      if (!members.isEmpty() && desired.isEmpty())
        conditions.add(new Condition(RoutingFailureReason.NO_VALID_RECIPIENT, null));
    }
    var active = assignees.findAllByTaskIdAndIsActiveTrue(taskId);
    Set<UUID> current = active.stream().map(TaskAssignee::getUserId).collect(Collectors.toSet());
    boolean changed = !current.equals(desired);
    if (changed) {
      taskVersionLocks.forceIncrement(task);
      active.stream().filter(a -> !desired.contains(a.getUserId())).forEach(TaskAssignee::delete);
      desired.stream()
          .filter(id -> !current.contains(id))
          .forEach(
              id -> {
                var assignment = TaskAssignee.assignToUser(taskId, id, AssignedBy.SYSTEM);
                assignment.setTenantId(tenantId);
                assignees.save(assignment);
              });
    }
    var open = repository.openFailures(tenantId, taskId);
    int resolved = 0;
    for (var failure : open) {
      if (!conditions.contains(new Condition(failure.reason(), failure.userId()))) {
        repository.resolve(tenantId, failure.id());
        resolved++;
      }
    }
    int opened = 0;
    for (var condition : conditions) {
      if (open.stream().noneMatch(f -> condition.equals(new Condition(f.reason(), f.userId())))) {
        UUID failureId =
            repository.open(
                tenantId, taskId, poolKey, condition.reason(), condition.userId(), revision);
        events.publish(new RoutingFailureOpenedEvent(tenantId, failureId, taskId));
        opened++;
      }
    }
    repository.evaluated(tenantId, taskId, revision);
    return new Evaluation(changed, opened, resolved);
  }

  private record Condition(RoutingFailureReason reason, UUID userId) {}
}
