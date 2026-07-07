package com.fabricmanagement.production.execution.stockunit.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "stock_unit_soft_hold",
    schema = "production",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_stock_unit_soft_hold_line_piece",
          columnNames = {"tenant_id", "quote_line_id", "stock_unit_id"})
    },
    indexes = {
      @Index(
          name = "idx_soft_hold_tenant_piece_status",
          columnList = "tenant_id, stock_unit_id, status"),
      @Index(
          name = "idx_soft_hold_tenant_line_status",
          columnList = "tenant_id, quote_line_id, status")
    })
@Getter
@NoArgsConstructor
public class StockUnitSoftHold extends BaseEntity {

  @Column(name = "quote_line_id", nullable = false)
  private UUID quoteLineId;

  @Column(name = "stock_unit_id", nullable = false)
  private UUID stockUnitId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private StockUnitSoftHoldStatus status = StockUnitSoftHoldStatus.ACTIVE;

  @Column(name = "released_at")
  private Instant releasedAt;

  public static StockUnitSoftHold place(UUID tenantId, UUID quoteLineId, UUID stockUnitId) {
    StockUnitSoftHold hold = new StockUnitSoftHold();
    hold.setTenantId(tenantId);
    hold.quoteLineId = quoteLineId;
    hold.stockUnitId = stockUnitId;
    hold.status = StockUnitSoftHoldStatus.ACTIVE;
    hold.onCreate();
    return hold;
  }

  public void reactivate() {
    status = StockUnitSoftHoldStatus.ACTIVE;
    releasedAt = null;
    onUpdate();
  }

  public boolean release(Instant releasedAt) {
    if (status == StockUnitSoftHoldStatus.RELEASED) {
      return false;
    }
    status = StockUnitSoftHoldStatus.RELEASED;
    this.releasedAt = releasedAt;
    onUpdate();
    return true;
  }

  @Override
  public String getModuleCode() {
    return "SUHOLD";
  }
}
