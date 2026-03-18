package com.fabricmanagement.flowboard.generator.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.flowboard.task.domain.ModuleType;
import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Event → Task eşleme şablonu.
 *
 * <p>SmartTaskGeneratorListener bir event aldığında bu şablonu bulur ve Task oluşturur.
 *
 * <p>Tablo: {@code flowboard.task_template}<br>
 * Docs: {@code 07-flowboard/smart-task-generator.md} — Bölüm 1. TaskTemplate
 */
@Entity
@Table(schema = "flowboard", name = "task_template")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskTemplate extends BaseEntity {

  /** Tetikleyici event tipi — "SalesOrderConfirmed", "BatchQcFailed", vb. */
  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  /** Başlık şablonu — "{salesOrder.orderNumber}" gibi placeholder'lar içerebilir. */
  @Column(name = "title_template", nullable = false, length = 500)
  private String titleTemplate;

  @Enumerated(EnumType.STRING)
  @Column(name = "task_type", nullable = false, length = 30)
  private TaskType taskType;

  /** null ise tüm modüller için geçerli. */
  @Enumerated(EnumType.STRING)
  @Column(name = "module_type", length = 30)
  private ModuleType moduleType;

  @Enumerated(EnumType.STRING)
  @Column(name = "default_priority", nullable = false, length = 10)
  private Priority defaultPriority;

  @Enumerated(EnumType.STRING)
  @Column(name = "default_assignee_role", nullable = false, length = 20)
  private AssigneeRole defaultAssigneeRole;

  /** Varsayılan süre tahmini (saat). Null ise belirsiz. */
  @Column(name = "estimated_hours", precision = 6, scale = 2)
  private BigDecimal estimatedHours;

  /**
   * Alt görev şablonları — JSONB olarak saklanır.
   *
   * <p>Format: {@code [{"title": "...", "order": 1}]}
   */
  @Column(name = "checklist_template", columnDefinition = "TEXT")
  private String checklistTemplate;

  /**
   * Otomatik atanacak etiket adları — JSONB olarak saklanır.
   *
   * <p>Format: {@code ["URGENT", "VIP_CLIENT"]}
   */
  @Column(name = "auto_labels", columnDefinition = "TEXT")
  private String autoLabels;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  // =========================================================================
  // FACTORY
  // =========================================================================

  public static TaskTemplate create(
      String eventType,
      String titleTemplate,
      TaskType taskType,
      ModuleType moduleType,
      Priority defaultPriority,
      AssigneeRole defaultAssigneeRole,
      BigDecimal estimatedHours) {
    var t = new TaskTemplate();
    t.eventType = eventType;
    t.titleTemplate = titleTemplate;
    t.taskType = taskType;
    t.moduleType = moduleType;
    t.defaultPriority = defaultPriority;
    t.defaultAssigneeRole = defaultAssigneeRole;
    t.estimatedHours = estimatedHours;
    t.isActive = true;
    return t;
  }

  public void deactivate() {
    this.isActive = false;
  }

  @Override
  protected String getModuleCode() {
    return "TMPL";
  }
}
