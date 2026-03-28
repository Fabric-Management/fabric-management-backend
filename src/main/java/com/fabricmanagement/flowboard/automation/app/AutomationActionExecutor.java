package com.fabricmanagement.flowboard.automation.app;

import com.fabricmanagement.flowboard.automation.domain.AutomationContext;
import com.fabricmanagement.flowboard.automation.domain.AutomationRule;
import com.fabricmanagement.flowboard.automation.domain.port.out.AutomationNotificationPort;
import com.fabricmanagement.flowboard.board.infra.repository.BoardRepository;
import com.fabricmanagement.flowboard.task.app.EscalationService;
import com.fabricmanagement.flowboard.task.app.TaskLabelService;
import com.fabricmanagement.flowboard.task.app.TaskService;
import com.fabricmanagement.flowboard.task.domain.EscalationType;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskStatus;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.fabricmanagement.flowboard.task.dto.CreateTaskRequest;
import com.fabricmanagement.flowboard.task.dto.UpdateTaskStatusRequest;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AutomationActionExecutor {

  private final TaskRepository taskRepo;
  private final TaskService taskService;
  private final AutomationNotificationPort notificationPort;
  private final TaskLabelService taskLabelService;
  private final EscalationService escalationService;
  private final BoardRepository boardRepo;
  private final ObjectMapper objectMapper;

  @Retryable(
      retryFor = Exception.class,
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2))
  public void executeAction(AutomationRule rule, Task task, AutomationContext context) {
    String actionJson = rule.getActionConfig();

    JsonNode node = parseJson(actionJson, rule.getName(), "actionConfig");
    if (node == null) {
      log.warn(
          "AutomationActionExecutor: could not parse actionConfig for rule='{}' — skipping",
          rule.getName());
      return;
    }

    switch (rule.getActionType()) {
      case CHANGE_STATUS -> {
        String newStatus = textOrNull(node, "newStatus");
        if (newStatus != null) {
          log.info("AutomationActionExecutor: CHANGE_STATUS task={} → {}", task.getId(), newStatus);
          taskService.updateStatus(
              task.getId(),
              new UpdateTaskStatusRequest(TaskStatus.valueOf(newStatus)),
              SystemUser.ID,
              true);
        }
      }

      case CREATE_TASK -> {
        String taskTypeStr = textOrNull(node, "taskType");
        String titleTemplate = textOrNull(node, "titleTemplate");
        if (taskTypeStr == null) {
          log.warn(
              "AutomationActionExecutor: CREATE_TASK has no taskType in actionConfig — skipping");
          return;
        }

        TaskType taskType;
        try {
          taskType = TaskType.valueOf(taskTypeStr);
        } catch (IllegalArgumentException e) {
          log.warn(
              "AutomationActionExecutor: invalid taskType='{}' in rule='{}' — skipping",
              taskTypeStr,
              rule.getName());
          return;
        }

        String title =
            titleTemplate != null && task.getTitle() != null
                ? titleTemplate.replace("{task.title}", task.getTitle())
                : (task.getTitle() != null ? task.getTitle() + " — " + taskTypeStr : taskTypeStr);

        // Idempotency: entityId null kontrolü + DB COUNT sorgusu
        if (task.getEntityId() != null) {
          if (taskRepo.existsOpenTaskByEntityAndType(
              task.getEntityType(), task.getEntityId(), taskType)) {
            log.info(
                "AutomationActionExecutor: CREATE_TASK idempotency — {} already exists for entity {}",
                taskType,
                task.getEntityId());
            return;
          }
        } else {
          log.debug(
              "AutomationActionExecutor: CREATE_TASK entityId null — idempotency check skipped for rule='{}'",
              rule.getName());
        }

        var req =
            new CreateTaskRequest(
                task.getBoardId(),
                title,
                null,
                taskType,
                task.getModuleType(),
                task.getPriority(),
                task.getDeadline(),
                null,
                task.getEntityType(),
                task.getEntityId(),
                "AUTOMATION_RULE",
                rule.getId());
        var newTask = taskService.createTask(req);
        if (!context.deeper().isDepthExceeded()) {
          log.debug(
              "AutomationActionExecutor: evaluating rules for newly created task={}",
              newTask.getId());
        }
      }

      case UPDATE_PRIORITY -> {
        double bonus = node.path("priorityBonus").asDouble(0);
        int newScore = (int) (task.getPriorityScore() + bonus);
        task.updatePriorityScore(newScore);
        taskRepo.save(task);
        log.info(
            "AutomationActionExecutor: UPDATE_PRIORITY task={} +{} → {}",
            task.getId(),
            bonus,
            newScore);
      }

      case NOTIFY_MANAGER -> {
        String message = textOrNull(node, "message");
        if (message != null && task.getTitle() != null) {
          message = message.replace("{task.title}", task.getTitle());
        }
        log.info(
            "AutomationActionExecutor: NOTIFY_MANAGER task={} msg='{}'", task.getId(), message);
        notificationPort.notifyManager(
            task.getTenantId(), task.getBoardId(), message, task.getId());
      }

      case ADD_LABEL -> {
        String labelName = textOrNull(node, "labelName");
        log.info("AutomationActionExecutor: ADD_LABEL task={} label={}", task.getId(), labelName);
        if (labelName != null) {
          taskLabelService.assignLabelByName(
              task.getTenantId(), task.getBoardId(), task.getId(), labelName, SystemUser.ID);
        }
      }

      case ESCALATE -> {
        String escalateTo = textOrNull(node, "escalateTo");
        log.info("AutomationActionExecutor: ESCALATE task={} to={}", task.getId(), escalateTo);
        UUID targetManagerId = resolveEscalationTarget(escalateTo, task);
        escalationService.escalate(
            task.getTenantId(),
            task.getId(),
            task.getTaskNumber(),
            EscalationType.TIME_EXCEEDED,
            targetManagerId,
            "AutomationEngine Rule Escalation",
            1);
      }

      default ->
          log.warn(
              "AutomationActionExecutor: unhandled actionType={} for rule='{}'",
              rule.getActionType(),
              rule.getName());
    }
  }

  private UUID resolveEscalationTarget(String escalateTo, Task task) {
    if (escalateTo == null || escalateTo.isBlank()) {
      log.warn("ESCALATE: escalateTo is empty — falling back to SystemUser");
      return SystemUser.ID;
    }

    if ("BOARD_MANAGER".equalsIgnoreCase(escalateTo)) {
      return boardRepo
          .findById(task.getBoardId())
          .map(
              board -> {
                UUID managerId = board.getManagerUserId();
                if (managerId != null) {
                  log.info(
                      "ESCALATE: resolved BOARD_MANAGER for board={} → user={}",
                      task.getBoardId(),
                      managerId);
                  return managerId;
                }
                log.warn(
                    "ESCALATE: board {} has no managerUserId set — falling back to SystemUser",
                    task.getBoardId());
                return SystemUser.ID;
              })
          .orElseGet(
              () -> {
                log.warn(
                    "ESCALATE: board {} not found — falling back to SystemUser", task.getBoardId());
                return SystemUser.ID;
              });
    }

    try {
      return UUID.fromString(escalateTo);
    } catch (IllegalArgumentException e) {
      log.warn("ESCALATE: unrecognized escalateTo='{}' — falling back to SystemUser", escalateTo);
      return SystemUser.ID;
    }
  }

  private JsonNode parseJson(String json, String ruleName, String field) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      log.error(
          "AutomationActionExecutor: failed to parse {} for rule='{}' — value='{}' error={}",
          field,
          ruleName,
          json,
          e.getMessage());
      return null;
    }
  }

  private String textOrNull(JsonNode node, String key) {
    JsonNode child = node.path(key);
    if (child.isMissingNode() || child.isNull()) return null;
    return child.asText();
  }
}
