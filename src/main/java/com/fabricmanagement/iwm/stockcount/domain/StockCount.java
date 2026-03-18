package com.fabricmanagement.iwm.stockcount.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.iwm.common.exception.IwmDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock_count", schema = "iwm")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockCount extends BaseEntity {

  @Column(name = "count_number", nullable = false, length = 100)
  private String countNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "count_type", nullable = false, length = 30)
  private StockCountType countType;

  @Column(name = "location_id", nullable = false)
  private UUID locationId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private StockCountStatus status;

  @Column(name = "planned_at", nullable = false)
  private LocalDate plannedAt;

  @Column(name = "started_at")
  private OffsetDateTime startedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  public StockCount(
      UUID tenantId,
      String countNumber,
      StockCountType countType,
      UUID locationId,
      LocalDate plannedAt,
      String notes) {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(countType, "countType must not be null");
    Objects.requireNonNull(locationId, "locationId must not be null");
    Objects.requireNonNull(plannedAt, "plannedAt must not be null");
    if (countNumber == null || countNumber.isBlank()) {
      throw new IwmDomainException("countNumber must not be blank");
    }
    this.setTenantId(tenantId);
    this.countNumber = countNumber;
    this.countType = countType;
    this.locationId = locationId;
    this.status = StockCountStatus.PLANNED;
    this.plannedAt = plannedAt;
    this.notes = notes;
    this.setIsActive(true);
  }

  public void start() {
    if (this.status != StockCountStatus.PLANNED) {
      throw new IwmDomainException("Only PLANNED counts can be started, current: " + this.status);
    }
    this.status = StockCountStatus.IN_PROGRESS;
    this.startedAt = OffsetDateTime.now();
  }

  public void complete() {
    if (this.status != StockCountStatus.IN_PROGRESS) {
      throw new IwmDomainException(
          "Only IN_PROGRESS counts can be completed, current: " + this.status);
    }
    this.status = StockCountStatus.COMPLETED;
    this.completedAt = OffsetDateTime.now();
  }

  @Override
  protected String getModuleCode() {
    return "IWM-CNT";
  }
}
