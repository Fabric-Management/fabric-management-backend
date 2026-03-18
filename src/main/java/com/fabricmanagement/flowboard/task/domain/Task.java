package com.fabricmanagement.flowboard.task.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
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

  /** İlk IN_PROGRESS geçişinde set edilir. */
  @Column(name = "started_at")
  private Instant startedAt;

  /** DONE geçişinde set edilir. DONE → IN_PROGRESS (reopen) durumunda null yapılır. */
  @Column(name = "completed_at")
  private Instant completedAt;

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
    requireStatus(TaskStatus.BACKLOG);
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
      throw new IllegalStateException(
          "Cannot start progress from status: "
              + this.status
              + ". Allowed: TODO, BLOCKED, DONE (reopen)");
    }
    this.status = TaskStatus.IN_PROGRESS;
    // startedAt sadece ilk IN_PROGRESS geçişinde set olur
    if (this.startedAt == null) {
      this.startedAt = Instant.now();
    }
    // Reopen: completedAt temizlenir
    this.completedAt = null;
  }

  /** Task'ı IN_REVIEW durumuna alır — IN_PROGRESS'ten. */
  public void submitForReview() {
    requireStatus(TaskStatus.IN_PROGRESS);
    this.status = TaskStatus.IN_REVIEW;
  }

  /**
   * Task'ı DONE durumuna alır — IN_REVIEW veya IN_PROGRESS'ten.
   *
   * <p>completedAt otomatik set edilir.
   */
  public void markDone() {
    if (this.status != TaskStatus.IN_REVIEW && this.status != TaskStatus.IN_PROGRESS) {
      throw new IllegalStateException(
          "Cannot mark DONE from status: " + this.status + ". Allowed: IN_REVIEW, IN_PROGRESS");
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
      throw new IllegalStateException("Cannot block a task in status: " + this.status);
    }
    this.status = TaskStatus.BLOCKED;
  }

  /** Task'ı CANCELLED durumuna alır — herhangi bir aktif durumdan. */
  public void cancel() {
    if (this.status == TaskStatus.DONE || this.status == TaskStatus.CANCELLED) {
      throw new IllegalStateException("Cannot cancel a task in status: " + this.status);
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

  /** Deadline'ı günceller. */
  public void updateDeadline(LocalDate deadline) {
    this.deadline = deadline;
  }

  /** Priority günceller. */
  public void updatePriority(Priority priority) {
    this.priority = priority;
  }

  /** Başlık günceller. */
  public void updateTitle(String title) {
    this.title = title;
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

  private void requireStatus(TaskStatus expected) {
    if (this.status != expected) {
      throw new IllegalStateException("Expected status " + expected + " but was: " + this.status);
    }
  }
}
