package com.fabricmanagement.product.fabric.domain.pricing;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * What a finishing process costs per metre or per kilogram, from a date (FABRIC-3c). A price with a
 * firm is that firm's tariff; a price without one is the general price, used for any firm that has
 * no tariff of its own and for work done at an own site. A row is never edited: a new price is a
 * new row with a later {@code validFrom}, so what a lot was priced at stays readable. A row entered
 * by mistake is withdrawn (soft delete). The unique index that stops two live prices for one
 * process, firm and date lives in the migration, because it needs a COALESCE over the optional
 * firm.
 */
@Entity
@Table(name = "prod_fabric_finishing_price", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FabricFinishingPrice extends BaseEntity {

  /** numeric(15, 4): eleven digits before the point, four after. */
  private static final int MAX_INTEGER_DIGITS = 11;

  private static final int MAX_FRACTION_DIGITS = 4;

  private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

  @Column(name = "process_id", nullable = false, updatable = false)
  private UUID processId;

  /** The firm the tariff belongs to; null for the general price. */
  @Column(name = "partner_id", updatable = false)
  private UUID partnerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "unit", nullable = false, length = 10, updatable = false)
  private FinishingChargeUnit unit;

  @Column(name = "unit_price", nullable = false, precision = 15, scale = 4, updatable = false)
  private BigDecimal unitPrice;

  @Column(name = "currency", nullable = false, length = 3, updatable = false)
  private String currency;

  @Column(name = "valid_from", nullable = false, updatable = false)
  private LocalDate validFrom;

  public static FabricFinishingPrice record(
      UUID tenantId,
      UUID processId,
      UUID partnerId,
      FinishingChargeUnit unit,
      BigDecimal unitPrice,
      String currency,
      LocalDate validFrom) {
    if (tenantId == null) {
      throw new FabricDomainException("Finishing price tenantId must not be null");
    }
    if (processId == null) {
      throw new FabricDomainException("A finishing price needs a process");
    }
    if (unit == null) {
      throw new FabricDomainException("A finishing price is per metre or per kilogram");
    }
    if (validFrom == null) {
      throw new FabricDomainException("A finishing price needs the date it applies from");
    }
    FabricFinishingPrice price = new FabricFinishingPrice();
    price.setTenantId(tenantId);
    price.processId = processId;
    price.partnerId = partnerId;
    price.unit = unit;
    price.unitPrice = requireAmount(unitPrice);
    price.currency = requireCurrency(currency);
    price.validFrom = validFrom;
    return price;
  }

  /** Whether the price has started by {@code date}; a later price may still replace it. */
  public boolean startedBy(LocalDate date) {
    return !validFrom.isAfter(date);
  }

  /** The firm's own tariff, as opposed to the general price. */
  public boolean isFirmTariff() {
    return partnerId != null;
  }

  /** Withdraws a price that was entered by mistake. */
  public void withdraw() {
    delete();
  }

  private static BigDecimal requireAmount(BigDecimal amount) {
    if (amount == null || amount.signum() < 0) {
      throw new FabricDomainException("A finishing price is zero or more");
    }
    BigDecimal trimmed = amount.stripTrailingZeros();
    if (trimmed.scale() > MAX_FRACTION_DIGITS
        || trimmed.precision() - trimmed.scale() > MAX_INTEGER_DIGITS) {
      throw new FabricDomainException(
          "A finishing price has at most "
              + MAX_INTEGER_DIGITS
              + " digits before the point and "
              + MAX_FRACTION_DIGITS
              + " after it");
    }
    return amount.setScale(MAX_FRACTION_DIGITS);
  }

  private static String requireCurrency(String currency) {
    if (currency == null || !CURRENCY.matcher(currency).matches()) {
      throw new FabricDomainException(
          "A finishing price currency is a three-letter code, e.g. GBP: " + currency);
    }
    return currency;
  }

  @Override
  protected String getModuleCode() {
    return "FFPR";
  }
}
