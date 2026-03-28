package com.fabricmanagement.flowboard.task.app.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.flowboard.automation.app.AutomationEngine;
import com.fabricmanagement.flowboard.automation.domain.AutomationContext;
import com.fabricmanagement.flowboard.automation.domain.AutomationTriggerType;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskDeadlineSchedulerJobTest {

  @Mock private TaskRepository taskRepository;
  @Mock private AutomationEngine automationEngine;

  @InjectMocks private TaskDeadlineSchedulerJob job;

  @BeforeEach
  void setUp() {}

  @Test
  void shouldTriggerEngineAndMarkFlagWhenTasksApproachDeadline() {
    // Given
    Task mockTask = mock(Task.class);
    when(mockTask.getId()).thenReturn(UUID.randomUUID());
    when(mockTask.getBoardId()).thenReturn(UUID.randomUUID());
    when(taskRepository.findTasksApproachingDeadline(any(LocalDate.class)))
        .thenReturn(List.of(mockTask));

    // When
    job.checkApproachingDeadlines();

    // Then
    verify(taskRepository).findTasksApproachingDeadline(any(LocalDate.class));
    verify(automationEngine)
        .evaluate(
            eq(mockTask),
            eq(AutomationTriggerType.DEADLINE_APPROACHING),
            any(AutomationContext.class));
    verify(mockTask).markDeadlineWarningFired();
    verify(taskRepository).save(mockTask);
  }

  @Test
  void shouldNotSaveTaskIfEngineEvaluationFails() {
    // Given
    Task mockTask = mock(Task.class);
    when(mockTask.getId()).thenReturn(UUID.randomUUID());
    when(mockTask.getBoardId()).thenReturn(UUID.randomUUID());
    when(taskRepository.findTasksApproachingDeadline(any(LocalDate.class)))
        .thenReturn(List.of(mockTask));

    doThrow(new RuntimeException("Engine failed"))
        .when(automationEngine)
        .evaluate(any(Task.class), any(AutomationTriggerType.class), any(AutomationContext.class));

    // When
    job.checkApproachingDeadlines();

    // Then
    verify(automationEngine).evaluate(any(), any(), any());
    verify(mockTask, never()).markDeadlineWarningFired();
    verify(taskRepository, never()).save(mockTask);
  }
}
