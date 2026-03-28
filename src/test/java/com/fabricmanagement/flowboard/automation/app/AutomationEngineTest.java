package com.fabricmanagement.flowboard.automation.app;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.flowboard.automation.domain.AutomationActionType;
import com.fabricmanagement.flowboard.automation.domain.AutomationContext;
import com.fabricmanagement.flowboard.automation.domain.AutomationRule;
import com.fabricmanagement.flowboard.automation.domain.AutomationTriggerType;
import com.fabricmanagement.flowboard.automation.domain.port.out.AutomationNotificationPort;
import com.fabricmanagement.flowboard.automation.infra.repository.AutomationRuleRepository;
import com.fabricmanagement.flowboard.task.app.EscalationService;
import com.fabricmanagement.flowboard.task.app.TaskLabelService;
import com.fabricmanagement.flowboard.task.app.TaskService;
import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskStatus;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AutomationEngine")
class AutomationEngineTest {

  @Mock private AutomationRuleRepository ruleRepo;
  @Mock private TaskRepository taskRepo;
  @Mock private TaskService taskService;
  @Mock private AutomationNotificationPort notificationPort;
  @Mock private TaskLabelService taskLabelService;
  @Mock private EscalationService escalationService;
  @Mock private com.fabricmanagement.flowboard.board.infra.repository.BoardRepository boardRepo;

  @Spy
  private com.fasterxml.jackson.databind.ObjectMapper objectMapper =
      new com.fasterxml.jackson.databind.ObjectMapper();

  @InjectMocks private AutomationActionExecutor actionExecutor;

  private AutomationEngine automationEngine;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    automationEngine = new AutomationEngine(ruleRepo, actionExecutor, objectMapper);
  }

  private static final UUID TASK_ID = UUID.randomUUID();
  private static final UUID BOARD_ID = UUID.randomUUID();

  @Nested
  @DisplayName("Max Depth Protection")
  class MaxDepthProtection {

    @Test
    @DisplayName("Derinlik 3'ü aşınca kural değerlendirmesi iptal edilir")
    void depthExceeded_abortsEvaluation() {
      Task task = mock(Task.class);
      when(task.getId()).thenReturn(TASK_ID);
      when(task.getBoardId()).thenReturn(BOARD_ID);

      AutomationContext deepContext =
          AutomationContext.initial(TASK_ID, BOARD_ID).deeper().deeper().deeper(); // depth = 3

      automationEngine.evaluate(
          task, AutomationTriggerType.STATUS_CHANGED, deepContext, "TODO", "DONE");

      verifyNoInteractions(ruleRepo);
    }
  }

  @Nested
  @DisplayName("Condition Evaluation")
  class ConditionEvaluation {

    @Test
    @DisplayName("Koşulsuz kural (null condition) her zaman çalışır")
    void nullCondition_alwaysExecutes() {
      Task task = buildMockTask(TaskType.QUALITY, TaskStatus.DONE);

      AutomationRule rule = mock(AutomationRule.class);
      when(rule.getTriggerType()).thenReturn(AutomationTriggerType.STATUS_CHANGED);
      when(rule.getTriggerConfig())
          .thenReturn("{\"fromStatus\": \"IN_PROGRESS\", \"toStatus\": \"DONE\"}");
      when(rule.getConditionConfig()).thenReturn(null);
      when(rule.getActionType()).thenReturn(AutomationActionType.NOTIFY_MANAGER);
      when(rule.getActionConfig()).thenReturn("{\"message\": \"done!\"}");
      when(rule.getName()).thenReturn("test-rule");
      when(rule.getExecutionCount()).thenReturn(0L);

      when(ruleRepo.findActiveByTenantAndTriggerTypeAndBoard(
              any(), eq(AutomationTriggerType.STATUS_CHANGED), eq(BOARD_ID)))
          .thenReturn(List.of(rule));
      when(ruleRepo.save(any())).thenReturn(rule);

      automationEngine.evaluate(
          task,
          AutomationTriggerType.STATUS_CHANGED,
          AutomationContext.initial(TASK_ID, BOARD_ID),
          "IN_PROGRESS",
          "DONE");

      verify(rule).markExecuted();
    }

    @Test
    @DisplayName("taskType koşulu uyuşmuyorsa aksiyon çalışmaz")
    void taskTypeConditionNotMet_skipsAction() {
      Task task = buildMockTask(TaskType.PRODUCTION, TaskStatus.DONE);

      AutomationRule rule = mock(AutomationRule.class);
      when(rule.getTriggerType()).thenReturn(AutomationTriggerType.STATUS_CHANGED);
      when(rule.getTriggerConfig()).thenReturn("{}");
      when(rule.getConditionConfig()).thenReturn("{\"taskType\": \"QUALITY\"}");
      when(rule.getName()).thenReturn("quality-only-rule");
      when(rule.getExecutionCount()).thenReturn(0L);

      when(ruleRepo.findActiveByTenantAndTriggerTypeAndBoard(any(), any(), any()))
          .thenReturn(List.of(rule));

      automationEngine.evaluate(
          task,
          AutomationTriggerType.STATUS_CHANGED,
          AutomationContext.initial(TASK_ID, BOARD_ID),
          "IN_PROGRESS",
          "DONE");

      verify(rule, never()).markExecuted();
    }

    @Test
    @DisplayName("[X2 FIX] triggerConfig fromStatus/toStatus uyuşmuyorsa kural çalışmaz")
    void triggerConfigNotMatched_skipsAction() {
      Task task = buildMockTask(TaskType.QUALITY, TaskStatus.IN_PROGRESS);

      AutomationRule rule = mock(AutomationRule.class);
      when(rule.getTriggerType()).thenReturn(AutomationTriggerType.STATUS_CHANGED);
      // Kural: IN_PROGRESS → DONE bekliyor
      when(rule.getTriggerConfig())
          .thenReturn("{\"fromStatus\": \"IN_PROGRESS\", \"toStatus\": \"DONE\"}");
      when(rule.getConditionConfig()).thenReturn(null);
      when(rule.getName()).thenReturn("done-only-rule");
      when(rule.getExecutionCount()).thenReturn(0L);

      when(ruleRepo.findActiveByTenantAndTriggerTypeAndBoard(any(), any(), any()))
          .thenReturn(List.of(rule));

      // Ama gerçek transition: TO_DO → IN_PROGRESS (DONE değil)
      automationEngine.evaluate(
          task,
          AutomationTriggerType.STATUS_CHANGED,
          AutomationContext.initial(TASK_ID, BOARD_ID),
          "TO_DO",
          "IN_PROGRESS");

      verify(rule, never()).markExecuted();
    }

    @Test
    @DisplayName("[EV4 FIX] Max execution count aşılınca kural atlanır")
    void maxExecutionCount_skipsRule() {
      Task task = buildMockTask(TaskType.QUALITY, TaskStatus.DONE);

      AutomationRule rule = mock(AutomationRule.class);
      when(rule.getTriggerType()).thenReturn(AutomationTriggerType.STATUS_CHANGED);
      when(rule.getTriggerConfig()).thenReturn("{}");
      when(rule.getConditionConfig()).thenReturn(null);
      when(rule.getName()).thenReturn("exhausted-rule");
      when(rule.getExecutionCount()).thenReturn(10_000L); // MAX_EXECUTION_COUNT

      when(ruleRepo.findActiveByTenantAndTriggerTypeAndBoard(any(), any(), any()))
          .thenReturn(List.of(rule));

      automationEngine.evaluate(
          task,
          AutomationTriggerType.STATUS_CHANGED,
          AutomationContext.initial(TASK_ID, BOARD_ID),
          "IN_PROGRESS",
          "DONE");

      verify(rule, never()).markExecuted();
    }
  }

  @Nested
  @DisplayName("UPDATE_PRIORITY action")
  class UpdatePriorityAction {

    @Test
    @DisplayName("priorityBonus doğru uygulanır")
    void updatePriority_bonusApplied() {
      Task task = buildMockTask(TaskType.QUALITY, TaskStatus.IN_PROGRESS);
      when(task.getPriorityScore()).thenReturn(50);

      AutomationRule rule = mock(AutomationRule.class);
      when(rule.getActionType()).thenReturn(AutomationActionType.UPDATE_PRIORITY);
      when(rule.getActionConfig()).thenReturn("{\"priorityBonus\": 20}");
      when(rule.getTriggerType()).thenReturn(AutomationTriggerType.LABEL_ADDED);
      when(rule.getTriggerConfig()).thenReturn("{}");
      when(rule.getConditionConfig()).thenReturn(null);
      when(rule.getName()).thenReturn("vip-priority");
      when(rule.getExecutionCount()).thenReturn(0L);

      when(ruleRepo.findActiveByTenantAndTriggerTypeAndBoard(any(), any(), any()))
          .thenReturn(List.of(rule));
      when(ruleRepo.save(any())).thenReturn(rule);
      when(taskRepo.save(any())).thenReturn(task);

      automationEngine.evaluate(
          task, AutomationTriggerType.LABEL_ADDED, AutomationContext.initial(TASK_ID, BOARD_ID));

      verify(task).updatePriorityScore(70);
      verify(taskRepo).save(task);
    }
  }

  // =========================================================================
  // HELPERS
  // =========================================================================

  private Task buildMockTask(TaskType type, TaskStatus status) {
    Task task = mock(Task.class);
    when(task.getId()).thenReturn(TASK_ID);
    when(task.getTenantId()).thenReturn(UUID.randomUUID());
    when(task.getBoardId()).thenReturn(BOARD_ID);
    when(task.getTaskType()).thenReturn(type);
    when(task.getStatus()).thenReturn(status);
    when(task.getPriority()).thenReturn(Priority.MEDIUM);
    return task;
  }
}
