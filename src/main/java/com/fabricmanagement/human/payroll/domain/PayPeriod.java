package com.fabricmanagement.human.payroll.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "human_pay_period", schema = "human",
    indexes = {
        @Index(name = "idx_pay_period_country", columnList = "tenant_id,country_code"),
        @Index(name = "idx_pay_period_status", columnList = "tenant_id,status")
    })
@Getter
@Setter
@NoArgsConstructor
public class PayPeriod extends BaseEntity {

    @Column(name = "period_code", nullable = false, length = 50)
    private String periodCode;

    @Column(name = "country_code", nullable = false, length = 8)
    private String countryCode;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PayPeriodStatus status = PayPeriodStatus.DRAFT;

    @Column(name = "frequency", length = 30)
    private String frequency;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by")
    private java.util.UUID lockedBy;

    @Builder
    public PayPeriod(String periodCode,
                     String countryCode,
                     LocalDate startDate,
                     LocalDate endDate,
                     PayPeriodStatus status,
                     String frequency,
                     Instant lockedAt,
                     java.util.UUID lockedBy) {
        this.periodCode = periodCode;
        this.countryCode = countryCode;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status != null ? status : PayPeriodStatus.DRAFT;
        this.frequency = frequency;
        this.lockedAt = lockedAt;
        this.lockedBy = lockedBy;
    }

    public void open() {
        this.status = PayPeriodStatus.OPEN;
    }

    public void lock(java.util.UUID actorId, Instant timestamp) {
        this.status = PayPeriodStatus.LOCKED;
        this.lockedAt = timestamp;
        this.lockedBy = actorId;
    }

    public void close() {
        this.status = PayPeriodStatus.CLOSED;
    }

    public boolean includes(LocalDate date) {
        return (date.isEqual(startDate) || date.isAfter(startDate))
            && (date.isEqual(endDate) || date.isBefore(endDate));
    }

    @Override
    protected String getModuleCode() {
        return "PPR";
    }
}

