package com.fabricmanagement.flowboard.task.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Geciken veya sorunlu görevler için yöneticilere iletilen otomatik bildirimlerin kaydı. */
@Entity
@Table(schema = "flowboard", name = "escalation_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EscalationLog extends BaseEntity {

  @Column(name = "task_id", nullable = false)
  private UUID taskId;

  @Enumerated(EnumType.STRING)
  @Column(name = "escalation_type", nullable = false, length = 30)
  private EscalationType escalationType;

  @Column(name = "escalated_to_user_id", nullable = false)
  private UUID escalatedToUserId;

  @Column(nullable = false, length = 500)
  private String message;

  @Column(name = "resolved_at")
  private OffsetDateTime resolvedAt;

  @Column(name = "resolved_by_user_id")
  private UUID resolvedByUserId;

  @Column(name = "resolution_note", length = 500)
  private String resolutionNote;

  public EscalationLog(
      UUID tenantId,
      UUID taskId,
      EscalationType escalationType,
      UUID escalatedToUserId,
      String message) {
    this.setTenantId(tenantId);
    this.taskId = taskId;
    this.escalationType = escalationType;
    this.escalatedToUserId = escalatedToUserId;
    this.message = message;
  }

  @Override
  protected String getModuleCode() {
    return "TSK";
  }

  // [D5 FIX] resolve() Clock injection ile güncellendi.
  public void resolve(UUID resolvedByUserId, String resolutionNote, Clock clock) {
    this.resolvedByUserId = resolvedByUserId;
    this.resolutionNote = resolutionNote;
    this.resolvedAt = OffsetDateTime.now(clock);
  }
}
