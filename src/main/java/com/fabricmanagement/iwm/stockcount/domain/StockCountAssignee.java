package com.fabricmanagement.iwm.stockcount.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Sayım görevlisi ataması. BaseEntity'den miras alarak multi-tenant, soft-delete ve audit
 * alanlarını taşır (CR-10-08).
 */
@Entity
@Table(name = "stock_count_assignee", schema = "iwm")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockCountAssignee extends BaseEntity {

  @Column(name = "stock_count_id", nullable = false)
  private UUID stockCountId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "assigned_zone", length = 100)
  private String assignedZone;

  public StockCountAssignee(UUID tenantId, UUID stockCountId, UUID userId, String assignedZone) {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(stockCountId, "stockCountId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");
    this.setTenantId(tenantId);
    this.stockCountId = stockCountId;
    this.userId = userId;
    this.assignedZone = assignedZone;
    this.setIsActive(true);
  }

  @Override
  protected String getModuleCode() {
    return "IWM-CNT";
  }
}
