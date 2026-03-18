package com.fabricmanagement.flowboard.task.dto;

import com.fabricmanagement.flowboard.task.domain.ModuleType;
import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskStatus;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Task detay yanıtı.
 *
 * @param id Task UUID
 * @param taskNumber TSK-XXXX
 * @param boardId Board UUID
 * @param boardGroupId Board grubu UUID (opsiyonel)
 * @param title Başlık
 * @param description Açıklama
 * @param taskType Task tipi
 * @param moduleType Modül tipi
 * @param priority Öncelik
 * @param priorityScore Hesaplanan skor
 * @param status Mevcut status
 * @param deadline Deadline
 * @param estimatedHours Tahmini süre
 * @param actualHours Gerçekleşen süre
 * @param isOverdue Deadline geçmiş mi
 * @param startedAt İlk IN_PROGRESS zamanı
 * @param completedAt DONE zamanı
 * @param entityType Polimorfik referans tipi
 * @param entityId Polimorfik referans ID
 */
public record TaskResponse(
    UUID id,
    String taskNumber,
    UUID boardId,
    UUID boardGroupId,
    String title,
    String description,
    TaskType taskType,
    ModuleType moduleType,
    Priority priority,
    Integer priorityScore,
    TaskStatus status,
    LocalDate deadline,
    BigDecimal estimatedHours,
    BigDecimal actualHours,
    boolean isOverdue,
    Instant startedAt,
    Instant completedAt,
    String entityType,
    UUID entityId) {

  /**
   * Task entity'sinden DTO oluşturur — Clock-aware.
   *
   * @param task kaynak entity
   * @param today Clock'tan alınan bugünkü tarih
   */
  public static TaskResponse from(Task task, LocalDate today) {
    return new TaskResponse(
        task.getId(),
        task.getTaskNumber(),
        task.getBoardId(),
        task.getBoardGroupId(),
        task.getTitle(),
        task.getDescription(),
        task.getTaskType(),
        task.getModuleType(),
        task.getPriority(),
        task.getPriorityScore(),
        task.getStatus(),
        task.getDeadline(),
        task.getEstimatedHours(),
        task.getActualHours(),
        task.isOverdue(today),
        task.getStartedAt(),
        task.getCompletedAt(),
        task.getEntityType(),
        task.getEntityId());
  }

  /**
   * Task entity'sinden DTO oluşturur.
   *
   * @deprecated Deterministic değildir — {@link #from(Task, LocalDate)} kullanın.
   */
  @Deprecated(forRemoval = true)
  public static TaskResponse from(Task task) {
    return from(task, LocalDate.now());
  }
}
