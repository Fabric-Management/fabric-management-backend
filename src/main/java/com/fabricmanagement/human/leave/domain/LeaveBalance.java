package com.fabricmanagement.human.leave.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "human_leave_balance", schema = "human",
    indexes = {
        @Index(name = "idx_leave_balance_employee", columnList = "tenant_id,employee_id"),
        @Index(name = "idx_leave_balance_country", columnList = "tenant_id,country_code")
    })
@Getter
@Setter
@NoArgsConstructor
public class LeaveBalance extends BaseEntity {

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "balance_days", nullable = false)
    private BigDecimal balanceDays = BigDecimal.ZERO;

    @Column(name = "carry_over_days", nullable = false)
    private BigDecimal carryOverDays = BigDecimal.ZERO;

    @Column(name = "pending_days", nullable = false)
    private BigDecimal pendingDays = BigDecimal.ZERO;

    @Column(name = "last_accrual_at")
    private Instant lastAccrualAt;

    @Column(name = "policy_pack_code", length = 100)
    private String policyPackCode;

    @Column(name = "policy_pack_version")
    private Integer policyPackVersion;

    @Column(name = "country_code", length = 8)
    private String countryCode;

    public void applyAccrual(BigDecimal accrualAmount, String policyPackCode, Integer policyPackVersion, Instant accrualTime) {
        this.balanceDays = this.balanceDays.add(accrualAmount);
        this.policyPackCode = policyPackCode;
        this.policyPackVersion = policyPackVersion;
        this.lastAccrualAt = accrualTime;
    }

    public void updateCarryOver(BigDecimal carryOver) {
        this.carryOverDays = carryOver;
    }

    @Override
    protected String getModuleCode() {
        return "LVB";
    }
}

