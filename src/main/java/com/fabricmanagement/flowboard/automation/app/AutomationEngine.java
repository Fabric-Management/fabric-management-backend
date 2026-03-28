package com.fabricmanagement.flowboard.automation.app;

import com.fabricmanagement.flowboard.automation.domain.AutomationContext;
import com.fabricmanagement.flowboard.automation.domain.AutomationRule;
import com.fabricmanagement.flowboard.automation.domain.AutomationTriggerType;
import com.fabricmanagement.flowboard.automation.infra.repository.AutomationRuleRepository;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
 *   <li>triggerConfig eşleşmesi kontrol edilir (fromStatus/toStatus)
 *   <li>Her kural için conditionConfig değerlendirilerek koşullar kontrol edilir
 *   <li>Koşullar sağlanıyorsa aksiyon çalıştırılır
 *   <li>{@link AutomationContext#isDepthExceeded()} → sonsuz döngü koruması (max 3 derinlik)
 *   <li>Kural başına max execution limiti kontrol edilir
 * </ol>
 *
 * <p>Docs: {@code 07-flowboard/smart-task-generator.md} — Bölüm 5. AutomationEngine
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutomationEngine {

  /** Bir kuralın maksimum çalıştırılma sınırı (aynı task için değil, toplamda). */
  private static final long MAX_EXECUTION_COUNT = 10_000;

  private final AutomationRuleRepository ruleRepo;
  private final AutomationActionExecutor actionExecutor;
  private final ObjectMapper objectMapper;

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
    // Fail-fast for unsupported triggers
    if (triggerType != AutomationTriggerType.STATUS_CHANGED
        && triggerType != AutomationTriggerType.TASK_CREATED
        && triggerType != AutomationTriggerType.TASK_ASSIGNED
        && triggerType != AutomationTriggerType.DEADLINE_APPROACHING
        && triggerType != AutomationTriggerType.LABEL_ADDED
        && triggerType != AutomationTriggerType.CHECKLIST_COMPLETED) {
      log.error(
          "AutomationEngine: Trigger type {} is NOT_YET_IMPLEMENTED. Aborting execution for task={}",
          triggerType,
          task.getId());
      return;
    }

    if (context.isDepthExceeded()) {
      log.warn(
          "AutomationEngine: max depth ({}) exceeded for task={} — aborting cascade",
          3,
          task.getId());
      return;
    }

    List<AutomationRule> rules =
        ruleRepo.findActiveByTenantAndTriggerTypeAndBoard(
            task.getTenantId(), triggerType, task.getBoardId());

    for (AutomationRule rule : rules) {
      try {
        if (rule.getExecutionCount() >= MAX_EXECUTION_COUNT) {
          log.warn(
              "AutomationEngine: rule '{}' max execution count ({}) reached — skipping",
              rule.getName(),
              MAX_EXECUTION_COUNT);
          continue;
        }

        if (!triggerConfigMatches(rule, oldStatus, newStatus)) {
          continue;
        }

        if (conditionMatches(rule, task)) {
          actionExecutor.executeAction(rule, task, context);
          rule.markExecuted();
          ruleRepo.save(rule);
          log.info(
              "AutomationEngine executed: rule='{}' for task={}", rule.getName(), task.getId());
        }
      } catch (Exception e) {
        // Aksiyon başarısız olursa kural devre dışı bırakılmaz — sadece loglanır.
        // Spring Retry sayesinde geçici hatalar atlatıldıktan sonra kalıcı hatalar buraya düşer.
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
  // TRIGGER CONFIG MATCHING
  // =========================================================================

  /**
   * triggerConfig JSON'ını parse eder ve trigger koşullarını kontrol eder.
   *
   * <p>STATUS_CHANGED trigger type için fromStatus/toStatus kontrolü yapılır.
   */
  private boolean triggerConfigMatches(AutomationRule rule, String oldStatus, String newStatus) {
    String triggerJson = rule.getTriggerConfig();
    if (isBlankConfig(triggerJson)) {
      return true; // Boş config → her zaman eşleşir
    }

    JsonNode node = parseJson(triggerJson, rule.getName(), "triggerConfig");
    if (node == null) {
      return true; // Parse hatası — güvenli tarafta kal, kuralı engelleme
    }

    if (rule.getTriggerType() == AutomationTriggerType.STATUS_CHANGED) {
      String expectedFrom = textOrNull(node, "fromStatus");
      if (expectedFrom != null && oldStatus != null && !expectedFrom.equals(oldStatus)) {
        return false;
      }

      String expectedTo = textOrNull(node, "toStatus");
      if (expectedTo != null && newStatus != null && !expectedTo.equals(newStatus)) {
        return false;
      }
    }

    // LABEL_ADDED: labelName kontrolü — event payload'dan gelecek (Faz 8.3)
    // DEADLINE_APPROACHING: hoursBeforeDeadline — scheduler tarafından tetiklenir
    // (Faz 8.4)
    return true;
  }

  // =========================================================================
  // CONDITION EVALUATION
  // =========================================================================

  /**
   * conditionConfig JSON'ını parse eder ve koşulları kontrol eder.
   *
   * <p>Desteklenen koşullar:
   *
   * <ul>
   *   <li>{@code {"taskType": "QUALITY"}} — task tipi kontrolü
   *   <li>{@code {"priority": ["HIGH", "CRITICAL"]}} — öncelik kontrolü (array)
   *   <li>{@code {"estimatedHoursGte": 8}} — tahmini süre kontrolü
   * </ul>
   */
  private boolean conditionMatches(AutomationRule rule, Task task) {
    String conditionJson = rule.getConditionConfig();
    if (isBlankConfig(conditionJson)) {
      return true; // Koşulsuz — her zaman çalış
    }

    JsonNode node = parseJson(conditionJson, rule.getName(), "conditionConfig");
    if (node == null) {
      return true; // Parse hatası — güvenli tarafta kal
    }

    // taskType kontrolü
    if (node.has("taskType")) {
      String expected = node.path("taskType").asText(null);
      if (expected != null && !task.getTaskType().name().equals(expected)) {
        return false;
      }
    }

    // estimatedHoursGte kontrolü
    if (node.has("estimatedHoursGte")) {
      double threshold = node.path("estimatedHoursGte").asDouble(0);
      if (task.getEstimatedHours() == null || task.getEstimatedHours().doubleValue() < threshold) {
        return false;
      }
    }

    // priority kontrolü — string veya array her ikisini de destekler
    if (node.has("priority")) {
      String taskPriority = task.getPriority().name();
      JsonNode priorityNode = node.path("priority");
      if (priorityNode.isArray()) {
        boolean matched = false;
        for (JsonNode p : priorityNode) {
          if (taskPriority.equals(p.asText())) {
            matched = true;
            break;
          }
        }
        if (!matched) return false;
      } else {
        // Tek string değer
        if (!taskPriority.equals(priorityNode.asText())) {
          return false;
        }
      }
    }

    return true;
  }

  // =========================================================================
  // JACKSON HELPERS
  // =========================================================================

  /**
   * JSON string'ini parse eder. Parse hatası durumunda null döner ve loglar. Null/boş config için
   * {@link #isBlankConfig(String)} kullan.
   */
  private JsonNode parseJson(String json, String ruleName, String field) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      log.error(
          "AutomationEngine: failed to parse {} for rule='{}' — value='{}' error={}",
          field,
          ruleName,
          json,
          e.getMessage());
      return null;
    }
  }

  /** JsonNode'dan string değer okur; eksik veya null node için null döner. */
  private String textOrNull(JsonNode node, String key) {
    JsonNode child = node.path(key);
    if (child.isMissingNode() || child.isNull()) return null;
    return child.asText();
  }

  /** Boş veya içeriksiz config kontrolü. */
  private boolean isBlankConfig(String json) {
    return json == null || json.isBlank() || json.equals("{}");
  }
}
