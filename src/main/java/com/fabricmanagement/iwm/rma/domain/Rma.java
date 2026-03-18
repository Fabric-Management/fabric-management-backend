package com.fabricmanagement.iwm.rma.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.iwm.common.exception.IwmDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "rma", schema = "iwm")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rma extends BaseEntity {

  @Column(name = "rma_number", nullable = false, length = 100)
  private String rmaNumber;

  @Column(name = "trading_partner_id", nullable = false)
  private UUID tradingPartnerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private RmaStatus status;

  @Column(name = "approved_at")
  private OffsetDateTime approvedAt;

  @Column(name = "approved_by")
  private UUID approvedBy;

  @Column(name = "rejected_at")
  private OffsetDateTime rejectedAt;

  @Column(name = "rejected_by")
  private UUID rejectedBy;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  public Rma(UUID tenantId, String rmaNumber, UUID tradingPartnerId, String notes) {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(tradingPartnerId, "tradingPartnerId must not be null");
    if (rmaNumber == null || rmaNumber.isBlank()) {
      throw new IwmDomainException("rmaNumber must not be blank");
    }
    this.setTenantId(tenantId);
    this.rmaNumber = rmaNumber;
    this.tradingPartnerId = tradingPartnerId;
    this.notes = notes;
    this.status = RmaStatus.PENDING;
    this.setIsActive(true);
  }

  public void approve(UUID approverId) {
    if (this.status != RmaStatus.PENDING) {
      throw new IwmDomainException("Only PENDING RMAs can be approved, current: " + this.status);
    }
    this.status = RmaStatus.APPROVED;
    this.approvedBy = approverId;
    this.approvedAt = OffsetDateTime.now();
  }

  public void reject(UUID rejectorId) {
    if (this.status != RmaStatus.PENDING) {
      throw new IwmDomainException("Only PENDING RMAs can be rejected, current: " + this.status);
    }
    Objects.requireNonNull(rejectorId, "rejectorId must not be null");
    this.status = RmaStatus.REJECTED;
    this.rejectedBy = rejectorId;
    this.rejectedAt = OffsetDateTime.now();
  }

  public void markReceived() {
    if (this.status != RmaStatus.APPROVED) {
      throw new IwmDomainException("Only APPROVED RMAs can be received, current: " + this.status);
    }
    this.status = RmaStatus.RECEIVED;
  }

  public void markProcessed() {
    if (this.status != RmaStatus.RECEIVED) {
      throw new IwmDomainException("Only RECEIVED RMAs can be processed, current: " + this.status);
    }
    this.status = RmaStatus.PROCESSED;
  }

  @Override
  protected String getModuleCode() {
    return "IWM-RMA";
  }
}
