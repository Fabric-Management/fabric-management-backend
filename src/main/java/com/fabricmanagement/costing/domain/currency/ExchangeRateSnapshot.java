package com.fabricmanagement.costing.domain.currency;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

/**
 * Immutable point-in-time exchange rate record.
 *
 * <p>When a {@link com.fabricmanagement.costing.domain.calculation.CostCalculation} involves a
 * non-base currency, a snapshot is captured so historical recalculations remain consistent even if
 * the live rate changes later.
 *
 * <p>Sources: TCMB (Turkish central bank), ECB (European central bank), or MANUAL overrides.
 */
@Entity
@Table(name = "exchange_rate_snapshot", schema = "costing")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRateSnapshot extends BaseEntity {

  @Column(name = "base_currency", nullable = false, length = 10)
  private String baseCurrency;

  @Column(name = "target_currency", nullable = false, length = 10)
  private String targetCurrency;

  @Column(name = "rate", nullable = false, precision = 20, scale = 8)
  private BigDecimal rate;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 20)
  private ExchangeRateSource source;

  @Column(name = "captured_at", nullable = false)
  private Instant capturedAt;

  /**
   * Factory method to capture an exchange rate snapshot.
   *
   * @param tenantId owning tenant
   * @param baseCurrency the base/denomination currency (the tenant's reporting currency)
   * @param targetCurrency the foreign currency
   * @param rate how many base units equal 1 target unit
   * @param source where the rate was obtained
   * @param capturedAt explicit timestamp (e.g. from TCMB API response); null → uses Instant.now()
   */
  public static ExchangeRateSnapshot capture(
      java.util.UUID tenantId,
      String baseCurrency,
      String targetCurrency,
      BigDecimal rate,
      ExchangeRateSource source,
      Instant capturedAt) {
    var snap = new ExchangeRateSnapshot();
    snap.setTenantId(tenantId);
    snap.setBaseCurrency(baseCurrency);
    snap.setTargetCurrency(targetCurrency);
    snap.setRate(rate);
    snap.setSource(source);
    snap.setCapturedAt(capturedAt != null ? capturedAt : Instant.now());
    snap.onCreate();
    return snap;
  }

  /** Convenience overload for manual/TCMB captures where the timestamp is always now. */
  public static ExchangeRateSnapshot capture(
      java.util.UUID tenantId,
      String baseCurrency,
      String targetCurrency,
      BigDecimal rate,
      ExchangeRateSource source) {
    return capture(tenantId, baseCurrency, targetCurrency, rate, source, null);
  }

  /**
   * Convert an amount from the target currency to the base currency.
   *
   * @param amount amount in the target currency
   * @return equivalent amount in the base currency
   */
  public BigDecimal toBase(BigDecimal amount) {
    return amount.multiply(rate).setScale(4, java.math.RoundingMode.HALF_UP);
  }

  @Override
  protected String getModuleCode() {
    return "EXRATE";
  }
}
