package com.fabricmanagement.iwm.stockcount.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Sayım tolerans kuralları. {@code autoAdjustThreshold} altındaki farklar otomatik düzeltilir,
 * {@code requiresManagerApproval} üzerindeki farklar yönetici onayı gerektirir.
 */
@Entity
@Table(name = "stock_count_tolerance", schema = "iwm")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockCountTolerance extends BaseEntity {

  @Column(name = "module_type", nullable = false, length = 50)
  private String moduleType;

  @Column(name = "auto_adjust_threshold", nullable = false, precision = 5, scale = 2)
  private BigDecimal autoAdjustThreshold;

  @Column(name = "requires_manager_approval", nullable = false, precision = 5, scale = 2)
  private BigDecimal requiresManagerApproval;

  public StockCountTolerance(
      UUID tenantId,
      String moduleType,
      BigDecimal autoAdjustThreshold,
      BigDecimal requiresManagerApproval) {
    java.util.Objects.requireNonNull(tenantId, "tenantId must not be null");
    java.util.Objects.requireNonNull(moduleType, "moduleType must not be null");
    this.setTenantId(tenantId);
    this.moduleType = moduleType;
    this.autoAdjustThreshold = autoAdjustThreshold;
    this.requiresManagerApproval = requiresManagerApproval;
    this.setIsActive(true);
  }

  @Override
  protected String getModuleCode() {
    return "IWM-CNT";
  }
}
