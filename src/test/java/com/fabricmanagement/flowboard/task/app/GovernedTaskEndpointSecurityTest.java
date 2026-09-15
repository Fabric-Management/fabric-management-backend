package com.fabricmanagement.flowboard.task.app;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException;
import com.fabricmanagement.flowboard.task.domain.ModuleType;
import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.fabricmanagement.flowboard.task.dto.CreateTaskRequest;
import com.fabricmanagement.flowboard.task.dto.UpdateTaskStatusRequest;
import com.fabricmanagement.flowboard.task.infra.repository.TaskAssigneeRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskLabelAssignmentRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskLabelRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GovernedTaskEndpointSecurityTest {

  @Mock private TaskRepository taskRepository;
  @Mock private TaskAssigneeRepository assigneeRepository;
  @Mock private TaskLabelAssignmentRepository labelAssignmentRepository;
  @Mock private TaskLabelRepository labelRepository;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private UserFacade userFacade;
  @Mock private TaskProvisioningService provisioningService;

  private TaskService service;
  private TaskWorkflowRegistry workflows;

  @BeforeEach
  void setUp() {
    workflows = new TaskWorkflowRegistry();
    service =
        new TaskService(
            taskRepository,
            assigneeRepository,
            labelAssignmentRepository,
            labelRepository,
            eventPublisher,
            userFacade,
            provisioningService,
            workflows);
  }

  @Test
  void statusAndCancelPathsRefuseAnyTaskPinnedToGovernedWorkflow() {
    UUID taskId = UUID.randomUUID();
    Task task = governedTask(taskId, TaskType.GENERAL);
    when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

    assertThatThrownBy(
            () ->
                service.updateStatus(
                    taskId,
                    new UpdateTaskStatusRequest(
                        com.fabricmanagement.flowboard.task.domain.TaskStatus.CANCELLED),
                    UUID.randomUUID(),
                    false))
        .isInstanceOf(FlowBoardDomainException.class)
        .hasMessageContaining("typed domain action");
    assertThatThrownBy(() -> service.cancelTask(taskId))
        .isInstanceOf(FlowBoardDomainException.class)
        .hasMessageContaining("typed domain action");
    verify(taskRepository, never()).save(any());
  }

  @Test
  void manualCreatePathRefusesATypeOwnedByTheGovernedWorkflow() {
    CreateTaskRequest request =
        new CreateTaskRequest(
            UUID.randomUUID(),
            "Manual order cover",
            null,
            TaskType.ORDER_COVER,
            ModuleType.GENERAL,
            Priority.MEDIUM,
            null,
            null,
            "SALES_ORDER",
            UUID.randomUUID(),
            "MANUAL",
            null);

    assertThatThrownBy(() -> service.createTask(request))
        .isInstanceOf(FlowBoardDomainException.class)
        .hasMessageContaining("domain provisioning path");
    verify(provisioningService, never()).createOrSynchronizeActive(any());
  }

  private Task governedTask(UUID taskId, TaskType type) {
    Task task =
        Task.create(
            "TSK-0001",
            UUID.randomUUID(),
            "Governed task with a legacy-looking type",
            type,
            ModuleType.GENERAL,
            Priority.MEDIUM,
            null,
            null,
            null,
            null);
    task.setId(taskId);
    task.govern("governed:key", TaskWorkflowRegistry.ORDER_COVER_DEFINITION_ID, 1);
    return task;
  }
}
