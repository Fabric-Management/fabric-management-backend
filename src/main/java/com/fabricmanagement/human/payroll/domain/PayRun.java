package com.fabricmanagement.human.payroll.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
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
@Table(
    name = "human_pay_run",
    schema = "human",
    indexes = {@Index(name = "idx_pay_run_status", columnList = "tenant_id,status")})
@Getter
@Setter
@NoArgsConstructor
public class PayRun extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "pay_period_id", nullable = false)
  private PayPeriod payPeriod;

  @Column(name = "run_number", nullable = false)
  private Integer runNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private PayRunStatus status = PayRunStatus.CREATED;

  @Column(name = "policy_pack_code", length = 100)
  private String policyPackCode;

  @Column(name = "policy_pack_version")
  private Integer policyPackVersion;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt = Instant.now();

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "initiated_by")
  private UUID initiatedBy;

  @Column(name = "notes")
  private String notes;

  @Builder
  public PayRun(
      PayPeriod payPeriod,
      Integer runNumber,
      PayRunStatus status,
      String policyPackCode,
      Integer policyPackVersion,
      Instant startedAt,
      Instant completedAt,
      UUID initiatedBy,
      String notes) {
    this.payPeriod = payPeriod;
    this.runNumber = runNumber;
    this.status = status != null ? status : PayRunStatus.CREATED;
    this.policyPackCode = policyPackCode;
    this.policyPackVersion = policyPackVersion;
    this.startedAt = startedAt != null ? startedAt : Instant.now();
    this.completedAt = completedAt;
    this.initiatedBy = initiatedBy;
    this.notes = notes;
  }

  public void markInProgress() {
    this.status = PayRunStatus.IN_PROGRESS;
    this.startedAt = Instant.now();
  }

  public void markValidated() {
    this.status = PayRunStatus.VALIDATED;
  }

  public void markLocked() {
    this.status = PayRunStatus.LOCKED;
  }

  public void markCompleted(Instant finishedAt) {
    this.status = PayRunStatus.COMPLETED;
    this.completedAt = finishedAt != null ? finishedAt : Instant.now();
  }

  public void markFailed(String failureNote) {
    this.status = PayRunStatus.FAILED;
    this.notes = failureNote;
    this.completedAt = Instant.now();
  }

  public boolean isFinalized() {
    return status == PayRunStatus.COMPLETED || status == PayRunStatus.CANCELLED;
  }

  @Override
  protected String getModuleCode() {
    return "PRN";
  }
}
