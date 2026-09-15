package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.board.infra.repository.BoardRepository;
import com.fabricmanagement.flowboard.task.domain.PinnedWorkflow;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskCreation;
import com.fabricmanagement.flowboard.task.domain.TaskStatus;
import com.fabricmanagement.flowboard.task.domain.event.TaskCreatedEvent;
import com.fabricmanagement.flowboard.task.infra.repository.TaskProvisioningRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Single writer for manual, generated, recurring, and automation Task creation. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskProvisioningService {

  private final TaskRepository taskRepository;
  private final TaskProvisioningRepository provisioningRepository;
  private final TaskAffectedSubjectService affectedSubjectService;
  private final BoardRepository boardRepository;
  private final PriorityScoreCalculator scoreCalculator;
  private final TaskWorkflowRegistry workflowRegistry;
  private final DomainEventPublisher eventPublisher;

  @Transactional
  public Task createOrSynchronizeActive(TaskCreation command) {
    validate(command);
    UUID tenantId = TenantContext.requireTenantId();
    boardRepository
        .findById(command.boardId())
        .orElseThrow(() -> new EntityNotFoundException("Board not found: " + command.boardId()));

    String taskNumber = "TSK-%04d".formatted(taskRepository.getNextTaskNumber());
    Task candidate =
        Task.create(
            taskNumber,
            command.boardId(),
            command.title(),
            command.taskType(),
            command.moduleType(),
            command.priority(),
            command.deadline(),
            command.estimatedHours(),
            command.entityType(),
            command.entityId());
    candidate.updateDescription(command.description());
    candidate.assignSource(command.sourceType(), command.sourceId());
    PinnedWorkflow workflow = workflowRegistry.latestFor(command.taskType());
    candidate.govern(command.generationKey(), workflow.definitionId(), workflow.version());
    candidate.updatePriorityScore(scoreCalculator.calculateWithLabels(candidate, List.of()));

    boolean created = provisioningRepository.insert(candidate, tenantId);
    Task task =
        taskRepository
            .findByTenantIdAndGenerationKeyAndIsActiveTrueAndClosedAtIsNull(
                tenantId, command.generationKey())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Task insert returned no active row for key " + command.generationKey()));

    affectedSubjectService.synchronize(task.getId(), command.affectedSubjects());
    if (created) {
      eventPublisher.publish(
          new TaskCreatedEvent(
              tenantId,
              task.getId(),
              task.getBoardId(),
              task.getTaskNumber(),
              TaskStatus.BACKLOG.name(),
              task.getTaskType().name()));
      log.info(
          "Task provisioned: taskId={} generationKey={} taskType={}",
          task.getId(),
          task.getGenerationKey(),
          task.getTaskType());
    }
    return task;
  }

  private static void validate(TaskCreation command) {
    if (command == null
        || command.boardId() == null
        || command.title() == null
        || command.title().isBlank()
        || command.taskType() == null
        || command.moduleType() == null
        || command.generationKey() == null
        || command.generationKey().isBlank()) {
      throw new IllegalArgumentException(
          "Task creation requires board, title, type, module, and key");
    }
  }
}
