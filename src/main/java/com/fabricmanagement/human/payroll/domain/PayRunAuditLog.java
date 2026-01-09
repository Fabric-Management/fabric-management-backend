package com.fabricmanagement.human.payroll.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "human_pay_run_audit_log", schema = "human")
@Getter
@Setter
@NoArgsConstructor
public class PayRunAuditLog extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "pay_run_id", nullable = false)
  private PayRun payRun;

  @Column(name = "action", nullable = false, length = 30)
  private String action;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(name = "message")
  private String message;

  @Column(name = "payload", columnDefinition = "jsonb")
  private String payload;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Builder
  public PayRunAuditLog(
      PayRun payRun,
      String action,
      UUID actorId,
      String message,
      String payload,
      Instant occurredAt) {
    this.payRun = payRun;
    this.action = action;
    this.actorId = actorId;
    this.message = message;
    this.payload = payload;
    this.occurredAt = occurredAt != null ? occurredAt : Instant.now();
  }

  @Override
  protected String getModuleCode() {
    return "PAU";
  }
}
