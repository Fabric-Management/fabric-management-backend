package com.fabricmanagement.human.payroll.domain;

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
@Table(name = "human_pay_run_payout", schema = "human")
@Getter
@Setter
@NoArgsConstructor
public class PayRunPayout extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "pay_run_id", nullable = false)
  private PayRun payRun;

  @Column(name = "employee_id", nullable = false)
  private UUID employeeId;

  @Column(name = "net_amount", nullable = false)
  private BigDecimal netAmount;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "payment_channel", length = 50)
  private String paymentChannel;

  @Column(name = "payout_reference", length = 100)
  private String payoutReference;

  @Column(name = "processed_at")
  private Instant processedAt;

  @Column(name = "status", nullable = false, length = 30)
  private String status = "PENDING";

  @Builder
  public PayRunPayout(
      PayRun payRun,
      UUID employeeId,
      BigDecimal netAmount,
      String currency,
      String paymentChannel,
      String payoutReference,
      Instant processedAt,
      String status) {
    this.payRun = payRun;
    this.employeeId = employeeId;
    this.netAmount = netAmount;
    this.currency = currency;
    this.paymentChannel = paymentChannel;
    this.payoutReference = payoutReference;
    this.processedAt = processedAt;
    if (status != null) {
      this.status = status;
    }
  }

  public void markProcessed(String reference, Instant processedAt) {
    this.status = "PROCESSED";
    this.payoutReference = reference;
    this.processedAt = processedAt != null ? processedAt : Instant.now();
  }

  public void markFailed(String reference) {
    this.status = "FAILED";
    this.payoutReference = reference;
  }

  @Override
  protected String getModuleCode() {
    return "PPO";
  }
}
