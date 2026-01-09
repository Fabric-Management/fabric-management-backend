package com.fabricmanagement.human.leave.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "human_leave_accrual_log", schema = "human")
@Getter
@Setter
@NoArgsConstructor
public class LeaveAccrualLog extends BaseEntity {

  @Column(name = "employee_id", nullable = false)
  private UUID employeeId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "leave_type_id", nullable = false)
  private LeaveType leaveType;

  @Column(name = "accrual_amount", nullable = false)
  private BigDecimal accrualAmount;

  @Column(name = "balance_after", nullable = false)
  private BigDecimal balanceAfter;

  @Column(name = "policy_pack_code", length = 100)
  private String policyPackCode;

  @Column(name = "policy_pack_version")
  private Integer policyPackVersion;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "context", columnDefinition = "jsonb")
  private String context;

  @Builder
  public LeaveAccrualLog(
      UUID employeeId,
      LeaveType leaveType,
      BigDecimal accrualAmount,
      BigDecimal balanceAfter,
      String policyPackCode,
      Integer policyPackVersion,
      Instant occurredAt,
      String context) {
    this.employeeId = employeeId;
    this.leaveType = leaveType;
    this.accrualAmount = accrualAmount;
    this.balanceAfter = balanceAfter;
    this.policyPackCode = policyPackCode;
    this.policyPackVersion = policyPackVersion;
    this.occurredAt = occurredAt;
    this.context = context;
  }

  @Override
  protected String getModuleCode() {
    return "LAL";
  }
}
