package com.fabricmanagement.flowboard.generator.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueSourceRow;
import com.fabricmanagement.flowboard.task.domain.ModuleType;
import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
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

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "description", length = 1000)
  private String description;

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

  /**
   * Catalogue identity (TASK-TEMPLATE-TENANCY-1). {@code null} = tenant-authored. Set only by the
   * catalogue distribution ({@link #catalogueCopy}, {@link #adoptCatalogKey}); never exposed or
   * writable through the template API, and never cleared.
   */
  @Column(name = "catalog_key", length = 100)
  private String catalogKey;

  // =========================================================================
  // FACTORY
  // =========================================================================

  public static TaskTemplate create(
      String name,
      String description,
      String eventType,
      String titleTemplate,
      TaskType taskType,
      ModuleType moduleType,
      Priority defaultPriority,
      AssigneeRole defaultAssigneeRole,
      BigDecimal estimatedHours,
      String autoLabels,
      String checklistTemplate) {
    var t = new TaskTemplate();
    t.name = name;
    t.description = description;
    t.eventType = eventType;
    t.titleTemplate = titleTemplate;
    t.taskType = taskType;
    t.moduleType = moduleType;
    t.defaultPriority = defaultPriority;
    t.defaultAssigneeRole = defaultAssigneeRole;
    t.estimatedHours = estimatedHours;
    t.autoLabels = autoLabels;
    t.checklistTemplate = checklistTemplate;
    t.isActive = true;
    return t;
  }

  public void update(
      String name,
      String description,
      String eventType,
      String titleTemplate,
      TaskType taskType,
      ModuleType moduleType,
      Priority defaultPriority,
      AssigneeRole defaultAssigneeRole,
      BigDecimal estimatedHours,
      String autoLabels,
      String checklistTemplate) {
    this.name = name;
    this.description = description;
    this.eventType = eventType;
    this.titleTemplate = titleTemplate;
    this.taskType = taskType;
    this.moduleType = moduleType;
    this.defaultPriority = defaultPriority;
    this.defaultAssigneeRole = defaultAssigneeRole;
    this.estimatedHours = estimatedHours;
    this.autoLabels = autoLabels;
    this.checklistTemplate = checklistTemplate;
  }

  /**
   * A tenant's active copy of a catalogue source row. Content is copied verbatim; the copy starts
   * active. uid, tenant and audit columns are assigned by {@link BaseEntity} on persist.
   */
  public static TaskTemplate catalogueCopy(CatalogueSourceRow source) {
    TaskTemplate copy =
        create(
            source.name(),
            source.description(),
            source.eventType(),
            source.titleTemplate(),
            TaskType.valueOf(source.taskType()),
            source.moduleType() == null ? null : ModuleType.valueOf(source.moduleType()),
            Priority.valueOf(source.defaultPriority()),
            AssigneeRole.valueOf(source.defaultAssigneeRole()),
            source.estimatedHours(),
            source.autoLabels(),
            source.checklistTemplate());
    copy.catalogKey = source.catalogKey();
    return copy;
  }

  /** R2: marks a seeded, unkeyed row as this tenant's catalogue row. Content is not touched. */
  public void adoptCatalogKey(String key) {
    if (this.catalogKey != null) {
      throw new IllegalStateException(
          "TaskTemplate " + getId() + " already carries catalogue key " + this.catalogKey);
    }
    this.catalogKey = key;
  }

  /**
   * Soft delete for catalogue rows. A hard delete would let the backfill re-create the row (R1);
   * the kept row with {@code deleted_at} set resolves to R0 instead. Both {@code is_active}
   * mappings are written ({@code TaskTemplate} and {@link BaseEntity} map the same column).
   */
  public void softDelete() {
    this.isActive = false;
    setIsActive(false);
    setDeletedAt(Instant.now());
  }

  public void deactivate() {
    this.isActive = false;
  }

  public void activate() {
    this.isActive = true;
  }

  @Override
  protected String getModuleCode() {
    return "TMPL";
  }
}
