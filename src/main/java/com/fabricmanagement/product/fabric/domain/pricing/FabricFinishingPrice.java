package com.fabricmanagement.product.fabric.domain.pricing;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingBillingBasis;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
import com.fabricmanagement.product.fabric.domain.vocabulary.GsmBasis;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
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
 * by mistake is withdrawn (soft delete).
 *
 * <p>A price may be narrowed by the quality grade of the metres, the dyehouse's price class, the
 * fabric's formation and a weight range (see {@link PriceScope}); the more of those a row names,
 * the more specific it is, and the most specific row wins (see {@link FinishingPriceResolver}). The
 * unique index that stops two live prices for one slot lives in the migration, because it needs a
 * COALESCE over the optional columns.
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
  @Column(name = "billing_basis", nullable = false, length = 10, updatable = false)
  private FinishingBillingBasis billingBasis;

  /** The quality grade of the metres; null for every grade without a row of its own. */
  @Column(name = "quality_grade_id", updatable = false)
  private UUID qualityGradeId;

  @Column(name = "price_class_id", updatable = false)
  private UUID priceClassId;

  @Enumerated(EnumType.STRING)
  @Column(name = "formation", length = 20, updatable = false)
  private FabricFormation formation;

  @Column(name = "gsm_from", precision = 7, scale = 1, updatable = false)
  private BigDecimal gsmFrom;

  @Column(name = "gsm_to", precision = 7, scale = 1, updatable = false)
  private BigDecimal gsmTo;

  @Enumerated(EnumType.STRING)
  @Column(name = "gsm_basis", length = 10, updatable = false)
  private GsmBasis gsmBasis;

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
      PriceScope scope,
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
    if (scope == null) {
      throw new FabricDomainException("A finishing price needs to say what it is for");
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
    price.partnerId = scope.partnerId();
    price.billingBasis = scope.billingBasis();
    price.qualityGradeId = scope.qualityGradeId();
    price.priceClassId = scope.priceClassId();
    price.formation = scope.formation();
    price.gsmFrom = scope.gsmFrom();
    price.gsmTo = scope.gsmTo();
    price.gsmBasis = scope.gsmBasis();
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

  /** Whom and what the price is for, as it was recorded. */
  public PriceScope scope() {
    return new PriceScope(
        partnerId, billingBasis, qualityGradeId, priceClassId, formation, gsmFrom, gsmTo, gsmBasis);
  }

  /**
   * Whether this row sits in the same slot as a price for {@code scope} from {@code from}: the same
   * firm, grade, class, formation, weight range and start date. The billing basis is not part of a
   * slot, because two rows that differ only in it would be ambiguous.
   */
  public boolean sameSlotAs(PriceScope scope, LocalDate from) {
    return validFrom.equals(from)
        && Objects.equals(partnerId, scope.partnerId())
        && Objects.equals(qualityGradeId, scope.qualityGradeId())
        && Objects.equals(priceClassId, scope.priceClassId())
        && formation == scope.formation()
        && gsmBasis == scope.gsmBasis()
        && Objects.equals(gsmFrom, scope.gsmFrom())
        && Objects.equals(gsmTo, scope.gsmTo());
  }

  /**
   * How narrowly the row is drawn: one for each of price class, formation and weight range it
   * names. The quality grade does not count, because it splits the metres into groups that are
   * priced separately rather than competing with each other.
   */
  public int specificity() {
    return (priceClassId != null ? 1 : 0)
        + (formation != null ? 1 : 0)
        + (gsmBasis != null ? 1 : 0);
  }

  /**
   * Whether the row's narrowings fit the lot. A narrowing the lot cannot be checked against (an
   * unknown weight, say) does not fit. The quality grade is not matched here: it is not a property
   * of the lot but of the metres that come back.
   */
  public boolean matches(PriceContext context) {
    if (priceClassId != null && !priceClassId.equals(context.priceClassId())) {
      return false;
    }
    if (formation != null && formation != context.formation()) {
      return false;
    }
    if (gsmBasis == null) {
      return true;
    }
    BigDecimal gsm = gsmBasis == GsmBasis.GREIGE ? context.gsmGreige() : context.gsmFinished();
    return gsm != null
        && (gsmFrom == null || gsm.compareTo(gsmFrom) >= 0)
        && (gsmTo == null || gsm.compareTo(gsmTo) <= 0);
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
