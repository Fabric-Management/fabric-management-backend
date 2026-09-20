package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the sales order behind a governed order-cover task without exposing the task entity to
 * the API layer. The tenant is checked explicitly as well as by RLS, as OrderCoverCapabilityAdapter
 * does, and a task of another type is indistinguishable from a missing one.
 */
@Component
@RequiredArgsConstructor
public class OrderCoverTaskResolver {
  private final TaskRepository tasks;

  public UUID requireOrderCoverSubject(UUID taskId) {
    UUID tenantId = TenantContext.requireTenantId();
    return tasks
        .findById(taskId)
        .filter(task -> tenantId.equals(task.getTenantId()))
        .filter(task -> task.getTaskType() == TaskType.ORDER_COVER)
        .map(Task::getEntityId)
        .orElseThrow(() -> new NotFoundException("Decision task not found"));
  }
}
