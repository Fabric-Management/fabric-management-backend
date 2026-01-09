package com.fabricmanagement.human.payroll.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "human_pay_run_entry", schema = "human")
@Getter
@Setter
@NoArgsConstructor
public class PayRunEntry extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "pay_run_id", nullable = false)
  private PayRun payRun;

  @Column(name = "employee_id", nullable = false)
  private UUID employeeId;

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_type", nullable = false, length = 20)
  private PayRunEntryType entryType;

  @Column(name = "code", nullable = false, length = 50)
  private String code;

  @Column(name = "description", length = 200)
  private String description;

  @Column(name = "amount", nullable = false)
  private BigDecimal amount;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "taxable", nullable = false)
  private boolean taxable = true;

  @Column(name = "policy_reference", columnDefinition = "jsonb")
  private String policyReference;

  @Builder
  public PayRunEntry(
      PayRun payRun,
      UUID employeeId,
      PayRunEntryType entryType,
      String code,
      String description,
      BigDecimal amount,
      String currency,
      boolean taxable,
      String policyReference) {
    this.payRun = payRun;
    this.employeeId = employeeId;
    this.entryType = entryType;
    this.code = code;
    this.description = description;
    this.amount = amount;
    this.currency = currency;
    this.taxable = taxable;
    this.policyReference = policyReference;
  }

  public boolean isEarning() {
    return entryType == PayRunEntryType.EARNING;
  }

  public boolean isDeduction() {
    return entryType == PayRunEntryType.DEDUCTION;
  }

  @Override
  protected String getModuleCode() {
    return "PRE";
  }
}
