package com.fabricmanagement.flowboard.task.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.board.domain.Board;
import com.fabricmanagement.flowboard.board.infra.repository.BoardRepository;
import com.fabricmanagement.flowboard.task.domain.ModuleType;
import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskCreation;
import com.fabricmanagement.flowboard.task.domain.TaskSubject;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.fabricmanagement.flowboard.task.infra.repository.TaskProvisioningRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskProvisioningServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("87702127-8db0-44e9-8225-91191b4b96b5");
  private static final UUID BOARD_ID = UUID.fromString("3becfb87-fb27-4545-91c2-10c67a27056d");
  private static final UUID SUBJECT_ID = UUID.fromString("75b2bb40-7a89-4b4e-874e-ed3525d9da2a");

  @Mock private TaskRepository taskRepository;
  @Mock private TaskProvisioningRepository provisioningRepository;
  @Mock private TaskAffectedSubjectService affectedSubjectService;
  @Mock private BoardRepository boardRepository;
  @Mock private PriorityScoreCalculator scoreCalculator;
  @Mock private DomainEventPublisher eventPublisher;

  private TaskProvisioningService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    service =
        new TaskProvisioningService(
            taskRepository,
            provisioningRepository,
            affectedSubjectService,
            boardRepository,
            scoreCalculator,
            new TaskWorkflowRegistry(),
            eventPublisher);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void createsThroughNativeConstraintAndPinsWorkflow() {
    String key = TaskGenerationKey.subject("SALES_ORDER", SUBJECT_ID, TaskType.ORDER_COVER, null);
    Task persisted = task(key);
    when(boardRepository.findById(BOARD_ID))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(Board.class)));
    when(taskRepository.getNextTaskNumber()).thenReturn(12L);
    when(scoreCalculator.calculateWithLabels(any(), any())).thenReturn(73);
    when(provisioningRepository.insert(any(), eq(TENANT_ID))).thenReturn(true);
    when(taskRepository.findByTenantIdAndGenerationKeyAndIsActiveTrueAndClosedAtIsNull(
            TENANT_ID, key))
        .thenReturn(Optional.of(persisted));

    Task result = service.createOrSynchronizeActive(command(key));

    ArgumentCaptor<Task> candidate = ArgumentCaptor.forClass(Task.class);
    verify(provisioningRepository).insert(candidate.capture(), eq(TENANT_ID));
    assertThat(candidate.getValue().getTaskNumber()).isEqualTo("TSK-0012");
    assertThat(candidate.getValue().getPriorityScore()).isEqualTo(73);
    assertThat(candidate.getValue().getWorkflowDefinitionId()).isNotNull();
    assertThat(candidate.getValue().getWorkflowVersion()).isEqualTo(1);
    assertThat(result).isSameAs(persisted);
    verify(affectedSubjectService).synchronize(eq(persisted.getId()), any());
    verify(eventPublisher).publish(any());
  }

  @Test
  void collisionReturnsExistingExecutionWithoutRepublishingCreatedEvent() {
    String key = TaskGenerationKey.subject("SALES_ORDER", SUBJECT_ID, TaskType.ORDER_COVER, null);
    Task persisted = task(key);
    when(boardRepository.findById(BOARD_ID))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(Board.class)));
    when(taskRepository.getNextTaskNumber()).thenReturn(13L);
    when(scoreCalculator.calculateWithLabels(any(), any())).thenReturn(10);
    when(provisioningRepository.insert(any(), eq(TENANT_ID))).thenReturn(false);
    when(taskRepository.findByTenantIdAndGenerationKeyAndIsActiveTrueAndClosedAtIsNull(
            eq(TENANT_ID), anyString()))
        .thenReturn(Optional.of(persisted));

    assertThat(service.createOrSynchronizeActive(command(key))).isSameAs(persisted);

    org.mockito.Mockito.verifyNoInteractions(eventPublisher);
  }

  private static TaskCreation command(String key) {
    return new TaskCreation(
        BOARD_ID,
        "Review order cover",
        null,
        TaskType.ORDER_COVER,
        ModuleType.GENERAL,
        Priority.HIGH,
        null,
        null,
        "SALES_ORDER",
        SUBJECT_ID,
        "TEMPLATE",
        UUID.randomUUID(),
        key,
        Set.of(new TaskSubject("SALES_ORDER", SUBJECT_ID)));
  }

  private static Task task(String key) {
    Task task =
        Task.create(
            "TSK-0012",
            BOARD_ID,
            "Review order cover",
            TaskType.ORDER_COVER,
            ModuleType.GENERAL,
            Priority.HIGH,
            null,
            null,
            "SALES_ORDER",
            SUBJECT_ID);
    task.setId(UUID.randomUUID());
    var workflow = new TaskWorkflowRegistry().latestFor(TaskType.ORDER_COVER);
    task.govern(key, workflow.definitionId(), workflow.version());
    return task;
  }
}
