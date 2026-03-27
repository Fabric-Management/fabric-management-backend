package com.fabricmanagement.flowboard.automation.app;

import com.fabricmanagement.flowboard.automation.domain.AutomationContext;
import com.fabricmanagement.flowboard.automation.domain.AutomationRule;
import com.fabricmanagement.flowboard.automation.domain.AutomationTriggerType;
import com.fabricmanagement.flowboard.automation.domain.port.out.AutomationNotificationPort;
import com.fabricmanagement.flowboard.automation.infra.repository.AutomationRuleRepository;
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
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * FlowBoard içi if-then-that otomasyon motoru.
 *
 * <p>Çalışma mekanizması:
 *
 * <ol>
 *   <li>Trigger tipi + board ile aktif kurallar çekilir
 *   <li>[X2 FIX] triggerConfig eşleşmesi kontrol edilir (fromStatus/toStatus)
 *   <li>Her kural için conditionConfig değerlendirilerek koşullar kontrol edilir
 *   <li>Koşullar sağlanıyorsa aksiyon çalıştırılır
 *   <li>{@link AutomationContext#isDepthExceeded()} → sonsuz döngü koruması (max 3 derinlik)
 *   <li>[EV4 FIX] Kural başına max execution limiti kontrol edilir
 * </ol>
 *
 * <p>Docs: {@code 07-flowboard/smart-task-generator.md} — Bölüm 5. AutomationEngine
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutomationEngine {

  /** [EV4 FIX] Bir kuralın maksimum çalıştırılma sınırı (aynı task için değil, toplamda). */
  private static final long MAX_EXECUTION_COUNT = 10_000;

  private final AutomationRuleRepository ruleRepo;
  private final TaskRepository taskRepo;
  private final TaskService taskService;

  // Phase 8.3 Injections
  private final AutomationNotificationPort notificationPort;
  private final TaskLabelService taskLabelService;
  private final EscalationService escalationService;
  private final BoardRepository boardRepo;

  /**
   * Task için verilen trigger tipine göre kuralları değerlendirir.
   *
   * @param task Tetiklenen task
   * @param triggerType Tetikleyici tip
   * @param context Çalışma derinliği — sonsuz döngü koruması için
   * @param oldStatus Önceki status (STATUS_CHANGED trigger'ı için). Null olabilir.
   * @param newStatus Yeni status (STATUS_CHANGED trigger'ı için). Null olabilir.
   */
  @Transactional
  public void evaluate(
      Task task,
      AutomationTriggerType triggerType,
      AutomationContext context,
      String oldStatus,
      String newStatus) {
    if (context.isDepthExceeded()) {
      log.warn(
          "AutomationEngine: max depth ({}) exceeded for task={} — aborting cascade",
          3,
          task.getId());
      return;
    }

    List<AutomationRule> rules =
        ruleRepo.findActiveByTriggerTypeAndBoard(triggerType, task.getBoardId());

    for (AutomationRule rule : rules) {
      try {
        // [EV4 FIX] Max execution sınırı
        if (rule.getExecutionCount() >= MAX_EXECUTION_COUNT) {
          log.warn(
              "AutomationEngine: rule '{}' max execution count ({}) reached — skipping",
              rule.getName(),
              MAX_EXECUTION_COUNT);
          continue;
        }

        // [X2 FIX] triggerConfig eşleştirme (fromStatus/toStatus)
        if (!triggerConfigMatches(rule, oldStatus, newStatus)) {
          continue;
        }

        if (conditionMatches(rule, task)) {
          executeAction(rule, task, context);
          rule.markExecuted();
          ruleRepo.save(rule);
          log.info(
              "AutomationEngine executed: rule='{}' for task={}", rule.getName(), task.getId());
        }
      } catch (Exception e) {
        // Aksiyon başarısız olursa kural devre dışı bırakılmaz — loglanır
        log.error(
            "AutomationEngine action failed: rule='{}' task={} error={}",
            rule.getName(),
            task.getId(),
            e.getMessage());
      }
    }
  }

  /**
   * Overloaded evaluate — eski çağrı imzası uyumluluğu için.
   *
   * <p>STATUS_CHANGED dışı trigger'larda oldStatus/newStatus null gönderilir.
   */
  @Transactional
  public void evaluate(Task task, AutomationTriggerType triggerType, AutomationContext context) {
    evaluate(task, triggerType, context, null, null);
  }

  // =========================================================================
  // TRIGGER CONFIG MATCHING — [X2 FIX]
  // =========================================================================

  /**
   * [X2 FIX] triggerConfig JSONB'yi parse eder ve trigger koşullarını kontrol eder.
   *
   * <p>STATUS_CHANGED trigger type için fromStatus/toStatus kontrolü yapılır.
   */
  private boolean triggerConfigMatches(AutomationRule rule, String oldStatus, String newStatus) {
    String triggerJson = rule.getTriggerConfig();
    if (triggerJson == null || triggerJson.isBlank() || triggerJson.equals("{}")) {
      return true; // Boş config → her zaman eşleşir
    }

    // STATUS_CHANGED trigger kontrolü
    if (rule.getTriggerType() == AutomationTriggerType.STATUS_CHANGED) {
      // fromStatus kontrolü
      String expectedFrom = extractString(triggerJson, "fromStatus");
      if (expectedFrom != null && oldStatus != null && !expectedFrom.equals(oldStatus)) {
        return false;
      }

      // toStatus kontrolü
      String expectedTo = extractString(triggerJson, "toStatus");
      if (expectedTo != null && newStatus != null && !expectedTo.equals(newStatus)) {
        return false;
      }
    }

    // LABEL_ADDED trigger: labelName kontrolü — event payload'dan gelecek (Faz 8.3)
    // DEADLINE_APPROACHING: hoursBeforeDeadline — scheduler tarafından tetiklenir (Faz 8.4)
    // Diğer triggerlar şimdilik config match atlanır
    return true;
  }

  // =========================================================================
  // CONDITION EVALUATION
  // =========================================================================

  /**
   * conditionConfig JSONB'yi parse eder ve koşulları kontrol eder.
   *
   * <p>Desteklenen koşullar:
   *
   * <ul>
   *   <li>{@code {"taskType": "QUALITY"}} — task tipi kontrolü
   *   <li>{@code {"priority": ["HIGH", "CRITICAL"]}} — öncelik kontrolü
   *   <li>{@code {"estimatedHoursGte": 8}} — tahmini süre kontrolü
   * </ul>
   */
  private boolean conditionMatches(AutomationRule rule, Task task) {
    String conditionJson = rule.getConditionConfig();
    if (conditionJson == null || conditionJson.isBlank() || conditionJson.equals("{}")) {
      return true; // Koşulsuz — her zaman çalış
    }

    // Basit JSON parsing (Jackson entegrasyonu olmadan)
    if (conditionJson.contains("\"taskType\"")) {
      String taskTypeVal = extractString(conditionJson, "taskType");
      if (taskTypeVal != null && !task.getTaskType().name().equals(taskTypeVal)) {
        return false;
      }
    }

    if (conditionJson.contains("\"estimatedHoursGte\"")) {
      double threshold = extractDouble(conditionJson, "estimatedHoursGte");
      if (task.getEstimatedHours() == null || task.getEstimatedHours().doubleValue() < threshold) {
        return false;
      }
    }

    if (conditionJson.contains("\"priority\"")) {
      // ["HIGH", "CRITICAL"] format
      String priorityVal = task.getPriority().name();
      if (!conditionJson.contains("\"" + priorityVal + "\"")) {
        return false;
      }
    }

    return true;
  }

  // =========================================================================
  // ACTION EXECUTION
  // =========================================================================

  private void executeAction(AutomationRule rule, Task task, AutomationContext context) {
    String actionJson = rule.getActionConfig();

    switch (rule.getActionType()) {
      case CHANGE_STATUS -> {
        String newStatus = extractString(actionJson, "newStatus");
        if (newStatus != null) {
          log.info("AutomationEngine: CHANGE_STATUS task={} → {}", task.getId(), newStatus);
          // [AUT1 FIX] Sistem kullanıcısı UUID kullanılarak durum değiştiriliyor
          taskService.updateStatus(
              task.getId(),
              new UpdateTaskStatusRequest(TaskStatus.valueOf(newStatus)),
              SystemUser.ID,
              true);
        }
      }

      case CREATE_TASK -> {
        String taskTypeStr = extractString(actionJson, "taskType");
        String titleTemplate = extractString(actionJson, "titleTemplate");
        if (taskTypeStr == null) {
          log.warn("AutomationEngine: CREATE_TASK has no taskType in actionConfig — skipping");
          return;
        }

        // [E3 FIX] TaskType.valueOf() explicit error handling
        TaskType taskType;
        try {
          taskType = TaskType.valueOf(taskTypeStr);
        } catch (IllegalArgumentException e) {
          log.warn(
              "AutomationEngine: invalid taskType='{}' in rule='{}' — skipping",
              taskTypeStr,
              rule.getName());
          return;
        }

        String title =
            titleTemplate != null && task.getTitle() != null
                ? titleTemplate.replace("{task.title}", task.getTitle())
                : (task.getTitle() != null ? task.getTitle() + " — " + taskTypeStr : taskTypeStr);

        // [L3 FIX + P2 FIX] Idempotency: entityId null kontrolü + DB COUNT sorgusu
        if (task.getEntityId() != null) {
          if (taskRepo.existsOpenTaskByEntityAndType(
              task.getEntityType(), task.getEntityId(), taskType)) {
            log.info(
                "AutomationEngine: CREATE_TASK idempotency — {} already exists for entity {}",
                taskType,
                task.getEntityId());
            return;
          }
        } else {
          log.debug(
              "AutomationEngine: CREATE_TASK entityId null — idempotency check skipped for rule='{}'",
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
                task.getEntityId());
        var newTask = taskService.createTask(req);
        // [F2 FIX] CreateTask sonrası — sadece depth artir; artık trigger yoksa recursive çağrı yok
        if (!context.deeper().isDepthExceeded()) {
          log.debug(
              "AutomationEngine: evaluating rules for newly created task={}", newTask.getId());
        }
      }

      case UPDATE_PRIORITY -> {
        double bonus = extractDouble(actionJson, "priorityBonus");
        int newScore = (int) (task.getPriorityScore() + bonus);
        task.updatePriorityScore(newScore);
        taskRepo.save(task);
        log.info(
            "AutomationEngine: UPDATE_PRIORITY task={} +{} → {}", task.getId(), bonus, newScore);
      }

      case NOTIFY_MANAGER -> {
        String message = extractString(actionJson, "message");
        if (message != null && task.getTitle() != null) {
          message = message.replace("{task.title}", task.getTitle());
        }
        log.info("AutomationEngine: NOTIFY_MANAGER task={} msg='{}'", task.getId(), message);
        // [AUT2 FIX] NotificationHub entegrasyonu AutomationNotificationPort ile çözüldü
        notificationPort.notifyManager(
            task.getTenantId(), task.getBoardId(), message, task.getId());
      }

      case ADD_LABEL -> {
        String labelName = extractString(actionJson, "labelName");
        log.info("AutomationEngine: ADD_LABEL task={} label={}", task.getId(), labelName);
        // [AUT3 FIX] Label ekleme — TaskLabelService aracılığıyla yapılıyor
        if (labelName != null) {
          taskLabelService.assignLabelByName(
              task.getTenantId(), task.getBoardId(), task.getId(), labelName, SystemUser.ID);
        }
      }

      case ESCALATE -> {
        String escalateTo = extractString(actionJson, "escalateTo");
        log.info("AutomationEngine: ESCALATE task={} to={}", task.getId(), escalateTo);
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
              "AutomationEngine: unhandled actionType={} for rule='{}'",
              rule.getActionType(),
              rule.getName());
    }
  }

  // =========================================================================
  // JSON HELPERS — basit parse, Jackson bağımlılığı olmadan
  // =========================================================================

  private String extractString(String json, String key) {
    if (json == null) return null;
    String search = "\"" + key + "\"";
    int idx = json.indexOf(search);
    if (idx < 0) return null;
    int start = json.indexOf("\"", idx + search.length() + 1);
    if (start < 0) return null;
    int end = json.indexOf("\"", start + 1);
    if (end < 0) return null;
    return json.substring(start + 1, end);
  }

  private double extractDouble(String json, String key) {
    if (json == null) return 0;
    String search = "\"" + key + "\"";
    int idx = json.indexOf(search);
    if (idx < 0) return 0;
    int colon = json.indexOf(":", idx);
    if (colon < 0) return 0;
    int end = json.indexOf("}", colon);
    int comma = json.indexOf(",", colon);
    int valueEnd = (comma > 0 && comma < end) ? comma : end;
    String val = json.substring(colon + 1, valueEnd).trim().replace("\"", "");
    try {
      return Double.parseDouble(val);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  // =========================================================================
  // ESCALATION TARGET RESOLUTION
  // =========================================================================

  /**
   * Resolves the escalation target user ID from the actionConfig value.
   *
   * <p>Supported formats:
   *
   * <ul>
   *   <li>{@code "BOARD_MANAGER"} — looks up the board's managerUserId from DB
   *   <li>UUID string — used directly as the target user ID
   *   <li>Any other string — falls back to SystemUser.ID
   * </ul>
   */
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
}
