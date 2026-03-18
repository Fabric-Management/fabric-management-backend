package com.fabricmanagement.iwm.rules.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.iwm.common.domain.UnitOfMeasure;
import com.fabricmanagement.iwm.common.exception.IwmDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "min_stock_rule", schema = "iwm")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MinStockRule extends BaseEntity {

  @Column(name = "location_id", nullable = false)
  private UUID locationId;

  @Column(name = "material_id", nullable = false)
  private UUID materialId;

  @Column(name = "min_qty", nullable = false, precision = 15, scale = 3)
  private BigDecimal minQty;

  @Enumerated(EnumType.STRING)
  @Column(name = "unit", nullable = false, length = 20)
  private UnitOfMeasure unit;

  @Column(name = "last_alert_at")
  private OffsetDateTime lastAlertAt;

  @Column(name = "alert_cooldown_hours", nullable = false)
  private Integer alertCooldownHours;

  public MinStockRule(
      UUID tenantId,
      UUID locationId,
      UUID materialId,
      BigDecimal minQty,
      UnitOfMeasure unit,
      Integer alertCooldownHours) {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(locationId, "locationId must not be null");
    Objects.requireNonNull(materialId, "materialId must not be null");
    Objects.requireNonNull(unit, "unit must not be null");
    if (minQty == null || minQty.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IwmDomainException("minQty must be positive");
    }
    this.setTenantId(tenantId);
    this.locationId = locationId;
    this.materialId = materialId;
    this.minQty = minQty;
    this.unit = unit;
    this.alertCooldownHours = alertCooldownHours != null ? alertCooldownHours : 24;
    this.setIsActive(true);
  }

  public void updateLastAlertAt(OffsetDateTime time) {
    this.lastAlertAt = time;
  }

  @Override
  protected String getModuleCode() {
    return "IWM-RUL";
  }
}
