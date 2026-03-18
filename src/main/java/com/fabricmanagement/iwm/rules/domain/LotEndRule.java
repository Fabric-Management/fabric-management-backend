package com.fabricmanagement.iwm.rules.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.iwm.common.domain.ProductionModuleType;
import com.fabricmanagement.iwm.common.domain.UnitOfMeasure;
import com.fabricmanagement.iwm.common.exception.IwmDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "lot_end_rule", schema = "iwm")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LotEndRule extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "module_type", nullable = false, length = 50)
  private ProductionModuleType moduleType;

  @Column(name = "threshold_qty", nullable = false, precision = 15, scale = 3)
  private BigDecimal thresholdQty;

  @Enumerated(EnumType.STRING)
  @Column(name = "unit", nullable = false, length = 20)
  private UnitOfMeasure unit;

  @Column(name = "show_warning", nullable = false)
  private Boolean showWarning;

  public LotEndRule(
      UUID tenantId,
      ProductionModuleType moduleType,
      BigDecimal thresholdQty,
      UnitOfMeasure unit,
      Boolean showWarning) {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(moduleType, "moduleType must not be null");
    Objects.requireNonNull(unit, "unit must not be null");
    if (thresholdQty == null || thresholdQty.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IwmDomainException("thresholdQty must be positive");
    }
    this.setTenantId(tenantId);
    this.moduleType = moduleType;
    this.thresholdQty = thresholdQty;
    this.unit = unit;
    this.showWarning = showWarning != null ? showWarning : true;
    this.setIsActive(true);
  }

  @Override
  protected String getModuleCode() {
    return "IWM-RUL";
  }
}
