package com.fabricmanagement.costing.domain.exchange;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "exchange_rate_cache", schema = "costing")
// Partial unique index: (tenant_id, base_currency, target_currency, rate_date) WHERE is_active =
// true
// Managed by V20260612120000__partial_unique_exchange_rate.sql
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExchangeRateCache extends BaseEntity {

  @Column(name = "base_currency", nullable = false, length = 3)
  private String baseCurrency; // USD

  @Column(name = "target_currency", nullable = false, length = 3)
  private String targetCurrency; // GBP

  @Column(name = "rate", nullable = false, precision = 15, scale = 6)
  private BigDecimal rate; // 38.500000

  @Column(name = "rate_date", nullable = false)
  private LocalDate rateDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 20)
  private ExchangeRateSource source; // MANUAL, TCMB, ECB

  @Override
  protected String getModuleCode() {
    return "FXR";
  }
}
