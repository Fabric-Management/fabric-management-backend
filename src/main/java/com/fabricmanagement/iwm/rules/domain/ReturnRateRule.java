package com.fabricmanagement.iwm.rules.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.iwm.common.exception.IwmDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "return_rate_rule", schema = "iwm")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReturnRateRule extends BaseEntity {

  @Column(name = "trading_partner_id", nullable = false)
  private UUID tradingPartnerId;

  @Column(name = "threshold_rate", nullable = false, precision = 5, scale = 2)
  private BigDecimal thresholdRate;

  @Column(name = "window_days", nullable = false)
  private Integer windowDays;

  public ReturnRateRule(
      UUID tenantId, UUID tradingPartnerId, BigDecimal thresholdRate, Integer windowDays) {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(tradingPartnerId, "tradingPartnerId must not be null");
    if (thresholdRate == null || thresholdRate.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IwmDomainException("thresholdRate must be positive");
    }
    this.setTenantId(tenantId);
    this.tradingPartnerId = tradingPartnerId;
    this.thresholdRate = thresholdRate;
    this.windowDays = windowDays != null ? windowDays : 90;
    this.setIsActive(true);
  }

  @Override
  protected String getModuleCode() {
    return "IWM-RUL";
  }
}
