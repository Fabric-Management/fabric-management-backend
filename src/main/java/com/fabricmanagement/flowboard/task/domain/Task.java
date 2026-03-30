package com.fabricmanagement.flowboard.task.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.flowboard.common.exception.TaskStatusTransitionException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FlowBoard operasyonel görevi.
 *
 * <p>Task durum makinesi:
 *
 * <pre>
 * BACKLOG → TO_DO → IN_PROGRESS → IN_REVIEW → DONE
 *                ↘ BLOCKED (herhangi bir yerden)
 *                ↘ CANCELLED
 * BLOCKED → IN_PROGRESS
 * DONE → IN_PROGRESS (yeniden açılırsa)
 * </pre>
 *
 * <p>PriorityScore otomatik hesaplanır: deadlineScore + taskTypeScore + entityScore + labelBonus.
 * Deadline geçtiyse → Integer.MAX_VALUE (kırmızı vurgu).
 *
 * <p>Docs: {@code 07-flowboard/board-task.md} — Bölüm 4 Task
 */
@Entity
@Table(schema = "flowboard", name = "task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task extends BaseEntity {

  @Column(name = "task_number", nullable = false, length = 20)
  private String taskNumber;

  @Column(name = "board_id", nullable = false, updatable = false)
  private UUID boardId;

  @Column(name = "board_group_id")
  private UUID boardGroupId;

  @Column(nullable = false, length = 500)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "task_type", nullable = false, length = 30)
  private TaskType taskType;

  @Enumerated(EnumType.STRING)
  @Column(name = "module_type", nullable = false, length = 30)
  private ModuleType moduleType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Priority priority;

  /** Dinamik öncelik skoru — PriorityScoreCalculator tarafından hesaplanır. */
  @Column(name = "priority_score", nullable = false)
  private Integer priorityScore = 0;

  @Column private LocalDate deadline;

  @Column(name = "estimated_hours", precision = 6, scale = 2)
  private BigDecimal estimatedHours;

  /** Toplam takip edilen süre — TaskTimeEntry kayıtlarından hesaplanır. */
  @Column(name = "actual_hours", precision = 6, scale = 2)
  private BigDecimal actualHours = BigDecimal.ZERO;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TaskStatus status = TaskStatus.BACKLOG;

  /** Polimorfik FK referans tipi — SALES_ORDER, WORK_ORDER, BATCH vb. */
  @Column(name = "entity_type", length = 50)
  private String entityType;

  /** Polimorfik FK — bağlı entity'nin UUID'si. */
  @Column(name = "entity_id")
  private UUID entityId;

  /** Task'ı oluşturan kaynak tipi (örn: MANUAL, TEMPLATE, AUTOMATION_RULE) - Phase 3 Audit Trail */
  @Column(name = "source_type", length = 30)
  private String sourceType = "MANUAL";

  /** Task'ı oluşturan kaynağın ID'si (örn: Template ID veya Rule ID) - Phase 3 Audit Trail */
  @Column(name = "source_id")
  private UUID sourceId;

  /** İlk IN_PROGRESS geçişinde set edilir. */
  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  /** Phase 3.1: Idempotency flag for approaching deadline warnings. */
  @Column(name = "is_deadline_warning_fired", nullable = false)
  private Boolean isDeadlineWarningFired = false;

  @Override
  protected String getModuleCode() {
    return "TSK";
  }

  // =========================================================================
  // FACTORY
  // =========================================================================

  /**
   * Yeni task oluşturur — başlangıç durumu: BACKLOG.
   *
   * @param taskNumber Otomatik üretilen numara (TSK-0001) — servis tarafından verilir
   * @param boardId Bağlı board
   * @param title Task başlığı
   * @param taskType Task tipi
   * @param moduleType Modül tipi
   * @param priority Öncelik
   * @param deadline Deadline
   * @param estimatedHours Tahmini süre
   * @param entityType Polimorfik referans tipi
   * @param entityId Polimorfik referans ID
   */
  public static Task create(
      String taskNumber,
      UUID boardId,
      String title,
      TaskType taskType,
      ModuleType moduleType,
      Priority priority,
      LocalDate deadline,
      BigDecimal estimatedHours,
      String entityType,
      UUID entityId) {
    var task = new Task();
    task.taskNumber = taskNumber;
    task.boardId = boardId;
    task.title = title;
    task.taskType = taskType;
    task.moduleType = moduleType;
    task.priority = priority != null ? priority : Priority.MEDIUM;
    task.deadline = deadline;
    task.estimatedHours = estimatedHours;
    task.entityType = entityType;
    task.entityId = entityId;
    task.status = TaskStatus.BACKLOG;
    return task;
  }

  // =========================================================================
  // STATUS MACHINE
  // =========================================================================

  /**
   * Task'ı TO_DO durumuna alır — BACKLOG'dan.
   *
   * @throws IllegalStateException geçersiz geçiş durumunda
   */
  public void startTodo() {
    if (this.status != TaskStatus.BACKLOG) {
      throw new TaskStatusTransitionException(this.status, TaskStatus.TODO);
    }
    this.status = TaskStatus.TODO;
  }

  /**
   * Task'ı IN_PROGRESS durumuna alır.
   *
   * <p>İlk IN_PROGRESS geçişinde startedAt set edilir. BLOCKED veya TO_DO'dan geçirebilir.
   */
  public void startProgress() {
    if (this.status != TaskStatus.TODO
        && this.status != TaskStatus.BLOCKED
        && this.status != TaskStatus.DONE) {
      throw new TaskStatusTransitionException(
          this.status, TaskStatus.IN_PROGRESS, "Allowed from: TODO, BLOCKED, DONE (reopen)");
    }
    this.status = TaskStatus.IN_PROGRESS;
    if (this.startedAt == null) {
      this.startedAt = Instant.now();
    }
    this.completedAt = null;
  }

  /** Task'ı IN_REVIEW durumuna alır — IN_PROGRESS'ten. */
  public void submitForReview() {
    if (this.status != TaskStatus.IN_PROGRESS) {
      throw new TaskStatusTransitionException(this.status, TaskStatus.IN_REVIEW);
    }
    this.status = TaskStatus.IN_REVIEW;
  }

  /**
   * Task'ı DONE durumuna alır — IN_REVIEW veya IN_PROGRESS'ten.
   *
   * <p>completedAt otomatik set edilir.
   */
  public void markDone() {
    if (this.status != TaskStatus.IN_REVIEW && this.status != TaskStatus.IN_PROGRESS) {
      throw new TaskStatusTransitionException(
          this.status, TaskStatus.DONE, "Allowed from: IN_REVIEW, IN_PROGRESS");
    }
    this.status = TaskStatus.DONE;
    this.completedAt = Instant.now();
  }

  /**
   * Task'ı BLOCKED durumuna alır — herhangi bir aktif durumdan.
   *
   * <p>DONE veya CANCELLED durumundan block edilemez.
   */
  public void block() {
    if (this.status == TaskStatus.DONE || this.status == TaskStatus.CANCELLED) {
      throw new TaskStatusTransitionException(this.status, TaskStatus.BLOCKED);
    }
    this.status = TaskStatus.BLOCKED;
  }

  public void cancel() {
    if (this.status == TaskStatus.DONE || this.status == TaskStatus.CANCELLED) {
      throw new TaskStatusTransitionException(this.status, TaskStatus.CANCELLED);
    }
    this.status = TaskStatus.CANCELLED;
  }

  // =========================================================================
  // MUTATORS
  // =========================================================================

  /** PriorityScore günceller — PriorityScoreCalculator çağrısı sonrası. */
  public void updatePriorityScore(Integer score) {
    this.priorityScore = score;
  }

  /** Deadline geçtiyse MAX priority score'u set eder. */
  public void escalatePriorityToMax() {
    this.priorityScore = Integer.MAX_VALUE;
  }

  /** Board grubunu günceller. */
  public void moveToBoardGroup(UUID boardGroupId) {
    this.boardGroupId = boardGroupId;
  }

  /** Actual hours günceller — TaskTimeEntry'lerden hesaplanmış toplam. */
  public void updateActualHours(BigDecimal actualHours) {
    this.actualHours = actualHours != null ? actualHours : BigDecimal.ZERO;
  }

  /** Task açıklamasını günceller. */
  public void updateDescription(String description) {
    this.description = description;
  }

  /** Deadline'ı günceller. IsDeadlineWarningFired flag'ini sıfırlar. */
  public void updateDeadline(LocalDate deadline) {
    if (this.deadline != null && !this.deadline.equals(deadline)) {
      this.isDeadlineWarningFired = false;
    } else if (this.deadline == null && deadline != null) {
      this.isDeadlineWarningFired = false;
    }
    this.deadline = deadline;
  }

  /** Phase 3.1: Deadline uyarı flag'ini işaretler. */
  public void markDeadlineWarningFired() {
    this.isDeadlineWarningFired = true;
  }

  /** Priority günceller. */
  public void updatePriority(Priority priority) {
    this.priority = priority;
  }

  /** Başlık günceller. */
  public void updateTitle(String title) {
    this.title = title;
  }

  /** Phase 3: Audit Trail için kaynak ataması. */
  public void assignSource(String sourceType, UUID sourceId) {
    if (sourceType != null && !sourceType.isBlank()) {
      this.sourceType = sourceType;
    }
    this.sourceId = sourceId;
  }

  // =========================================================================
  // QUERY HELPERS
  // =========================================================================

  /** Task aktif (DONE/CANCELLED olmayan) mi? */
  public boolean isOpen() {
    return this.status != TaskStatus.DONE && this.status != TaskStatus.CANCELLED;
  }

  /** Task insan tarafından aktif çalışılıyor mu? */
  public boolean isInProgress() {
    return this.status == TaskStatus.IN_PROGRESS;
  }

  // [L5 FIX] isOverdue() artık dışarıdan LocalDate alıyor — test'te
  // deterministic,
  // Clock injection ile uyumlu. Eski overload @Deprecated olarak korunuyor.

  /**
   * Deadline geçmiş ve task hala açık mı?
   *
   * @param today Clock'tan alınan bugünkü tarih
   */
  public boolean isOverdue(LocalDate today) {
    return deadline != null && today.isAfter(deadline) && isOpen();
  }

  /**
   * @deprecated Deterministic değildir — {@link #isOverdue(LocalDate)} kullanın.
   */
  @Deprecated(forRemoval = true)
  public boolean isOverdue() {
    return isOverdue(LocalDate.now());
  }

  // =========================================================================
  // PRIVATE HELPERS
  // =========================================================================
}
