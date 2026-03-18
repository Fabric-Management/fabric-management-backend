package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.fabricmanagement.flowboard.task.infra.repository.TaskLabelAssignmentRepository;
import com.fabricmanagement.flowboard.task.infra.repository.TaskLabelRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Task öncelik skoru hesaplar.
 *
 * <p>Formül:
 *
 * <pre>
 *   priorityScore = deadlineScore + taskTypeScore + entityScore + labelBonus
 *
 *   deadlineScore  = max(0, daysOverdue) * 10    (deadline geçtiyse → Integer.MAX_VALUE)
 *   taskTypeScore  = QUALITY:50 / SHIPMENT:40 / PRODUCTION:30 / PLANNING:20 / diğer:10
 *   entityScore    = priority enum değerine göre (CRITICAL:40 / HIGH:20 / MEDIUM:10 / LOW:5)
 *   labelBonus     = VIP_CLIENT:+20 / URGENT:+15 / FIRST_ORDER:+10 / REWORK:+5
 * </pre>
 *
 * <p>Docs: {@code 07-flowboard/board-task.md} — PriorityScore Hesaplama
 */
@Component
@RequiredArgsConstructor
public class PriorityScoreCalculator {

  private static final Map<String, Integer> LABEL_BONUS_MAP =
      Map.of(
          "VIP_CLIENT", 20,
          "URGENT", 15,
          "FIRST_ORDER", 10,
          "REWORK", 5);

  private static final Map<TaskType, Integer> TASK_TYPE_SCORE_MAP =
      Map.of(
          TaskType.QUALITY, 50,
          TaskType.SHIPMENT, 40,
          TaskType.PRODUCTION, 30,
          TaskType.APPROVAL, 25,
          TaskType.PLANNING, 20,
          TaskType.PROCUREMENT, 15,
          TaskType.WAREHOUSE, 15);

  private final TaskLabelAssignmentRepository labelAssignmentRepo;
  private final TaskLabelRepository labelRepo;
  private final Clock clock;

  /**
   * Task için priority score hesaplar — etiketler DB'den çekilir.
   *
   * @param task Score hesaplanacak task
   * @return Hesaplanan score — deadline geçtiyse Integer.MAX_VALUE
   */
  public int calculate(Task task) {
    int baseScore = computeBaseScore(task);
    if (baseScore == Integer.MAX_VALUE) return baseScore;
    return baseScore + calculateLabelBonus(task.getId());
  }

  /**
   * Task ID vermeden hesaplama — yeni task oluştururken etiket henüz atanmamış. Etiket listesi
   * dışarıdan verilir.
   */
  public int calculateWithLabels(Task task, List<String> labelNames) {
    int baseScore = computeBaseScore(task);
    if (baseScore == Integer.MAX_VALUE) return baseScore;

    // Label bonus — dışarıdan verilen isimler
    int labelBonus = 0;
    for (String labelName : labelNames) {
      labelBonus += LABEL_BONUS_MAP.getOrDefault(labelName, 0);
    }
    return baseScore + labelBonus;
  }

  // =========================================================================
  // [Q1 FIX] Private helpers — tekrar eden kod çıkarıldı
  // =========================================================================

  /**
   * Base score hesaplar: deadline + taskType + priority. Label bonus HARİÇ — çağıran tarafta
   * eklenir.
   */
  private int computeBaseScore(Task task) {
    LocalDate today = LocalDate.now(clock);
    // Deadline geçtiyse direkt MAX — kırmızı alarm
    if (task.isOverdue(today)) {
      return Integer.MAX_VALUE;
    }

    int score = 0;

    // 1. Deadline score
    if (task.getDeadline() != null) {
      long daysUntilDeadline = ChronoUnit.DAYS.between(today, task.getDeadline());
      if (daysUntilDeadline <= 0) {
        return Integer.MAX_VALUE; // bugün deadline veya geçmiş
      }
      if (daysUntilDeadline <= 7) {
        score += (int) ((7 - daysUntilDeadline) * 10);
      }
    }

    // 2. TaskType score
    score += TASK_TYPE_SCORE_MAP.getOrDefault(task.getTaskType(), 10);

    // 3. Priority (entity importance) score
    score += priorityTypeScore(task.getPriority());

    return score;
  }

  private int priorityTypeScore(Priority priority) {
    if (priority == null) return 10;
    return switch (priority) {
      case CRITICAL -> 40;
      case HIGH -> 20;
      case MEDIUM -> 10;
      case LOW -> 5;
    };
  }

  /** [P1 FIX] N+1 sorgu düzeltmesi — batch fetch ile tüm label'ları tek sorguda alır. */
  private int calculateLabelBonus(UUID taskId) {
    if (taskId == null) return 0;
    var assignments = labelAssignmentRepo.findAllByTaskId(taskId);
    if (assignments.isEmpty()) return 0;

    // Tüm label ID'lerini topla ve tek sorguda çek
    var labelIds = assignments.stream().map(a -> a.getLabelId()).collect(Collectors.toList());
    var labels = labelRepo.findAllById(labelIds);

    int bonus = 0;
    for (var label : labels) {
      bonus += LABEL_BONUS_MAP.getOrDefault(label.getName(), 0);
    }
    return bonus;
  }
}
