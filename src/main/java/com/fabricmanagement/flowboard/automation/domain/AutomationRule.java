package com.fabricmanagement.flowboard.automation.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FlowBoard içi if-then-that otomasyon kuralı.
 *
 * <p>Tablo: {@code flowboard.automation_rule}<br>
 * Docs: {@code 07-flowboard/smart-task-generator.md} — Bölüm 5. AutomationRule
 */
@Entity
@Table(schema = "flowboard", name = "automation_rule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AutomationRule extends BaseEntity {

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "trigger_type", nullable = false, length = 40)
  private AutomationTriggerType triggerType;

  /** Tetikleyici detayları — JSONB. Örnek: {"fromStatus": "IN_PROGRESS", "toStatus": "DONE"} */
  @Column(name = "trigger_config", columnDefinition = "TEXT", nullable = false)
  private String triggerConfig;

  /** "and only if" koşulları — JSONB. Null ise her zaman çalışır. Örnek: {"taskType": "QUALITY"} */
  @Column(name = "condition_config", columnDefinition = "TEXT")
  private String conditionConfig;

  @Enumerated(EnumType.STRING)
  @Column(name = "action_type", nullable = false, length = 30)
  private AutomationActionType actionType;

  /** Aksiyon detayları — JSONB. Örnek: {"newStatus": "IN_REVIEW"} */
  @Column(name = "action_config", columnDefinition = "TEXT", nullable = false)
  private String actionConfig;

  /** Null ise global (tüm board'larda aktif). */
  @Column(name = "board_id")
  private UUID boardId;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  /** Kaç kez çalıştı. */
  @Column(name = "execution_count", nullable = false)
  private long executionCount = 0;

  @Column(name = "last_executed_at")
  private Instant lastExecutedAt;

  /** Kuralı oluşturan kullanıcı. */
  @Column(name = "created_by_user_id")
  private UUID createdByUserId;

  // =========================================================================
  // FACTORY
  // =========================================================================

  public static AutomationRule create(
      String name,
      AutomationTriggerType triggerType,
      String triggerConfig,
      String conditionConfig,
      AutomationActionType actionType,
      String actionConfig,
      UUID boardId,
      UUID createdByUserId) {
    var r = new AutomationRule();
    r.name = name;
    r.triggerType = triggerType;
    r.triggerConfig = triggerConfig;
    r.conditionConfig = conditionConfig;
    r.actionType = actionType;
    r.actionConfig = actionConfig;
    r.boardId = boardId;
    r.createdByUserId = createdByUserId;
    r.isActive = true;
    r.executionCount = 0;
    return r;
  }

  // =========================================================================
  // DOMAIN METHODS
  // =========================================================================

  /** Çalıştırma kaydı günceller. */
  public void markExecuted() {
    this.executionCount++;
    this.lastExecutedAt = Instant.now();
  }

  public void deactivate() {
    this.isActive = false;
  }

  @Override
  protected String getModuleCode() {
    return "ARULE";
  }

  public void update(
      String name,
      String description,
      AutomationTriggerType triggerType,
      String triggerConfig,
      String conditionConfig,
      AutomationActionType actionType,
      String actionConfig,
      UUID boardId) {
    this.name = name;
    this.description = description;
    this.triggerType = triggerType;
    this.triggerConfig = triggerConfig;
    this.conditionConfig = conditionConfig;
    this.actionType = actionType;
    this.actionConfig = actionConfig;
    this.boardId = boardId;
  }

  public void toggleActive(boolean isActive) {
    this.isActive = isActive;
  }
}
