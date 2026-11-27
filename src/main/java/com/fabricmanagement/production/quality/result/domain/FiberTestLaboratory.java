package com.fabricmanagement.production.quality.result.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "production_quality_fiber_test_laboratory", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FiberTestLaboratory extends BaseEntity {

  @Column(name = "trading_partner_id", nullable = false, updatable = false)
  private UUID tradingPartnerId;

  public FiberTestLaboratory(UUID tradingPartnerId) {
    this.tradingPartnerId = tradingPartnerId;
  }

  @Override
  protected String getModuleCode() {
    return "FTL";
  }
}
