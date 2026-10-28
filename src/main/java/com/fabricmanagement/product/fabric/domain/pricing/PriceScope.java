package com.fabricmanagement.product.fabric.domain.pricing;

import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingBillingBasis;
import com.fabricmanagement.product.fabric.domain.vocabulary.GsmBasis;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Whom and what a finishing price is for (FABRIC-3c): the firm (or none, for the general price) and
 * every way the price may be narrowed. A narrowing left empty means "any".
 *
 * <p>The weight range is inclusive at both ends and either end may be open ("up to 150 gsm", "200
 * and over"); it is read against the greige or the finished weight, so a basis is given exactly
 * when a range is.
 *
 * @param partnerId the firm the tariff belongs to; null for the general price
 * @param billingBasis which metres the price multiplies; defaults to what came back
 * @param qualityGradeId the quality grade of the metres; null for every grade without a row of its
 *     own, and a price of zero makes a grade free
 * @param priceClassId the dyehouse tier
 * @param formation woven, knitted or nonwoven
 * @param gsmFrom lower end of the weight range
 * @param gsmTo upper end of the weight range
 * @param gsmBasis whether the range is the greige or the finished weight
 */
public record PriceScope(
    UUID partnerId,
    FinishingBillingBasis billingBasis,
    UUID qualityGradeId,
    UUID priceClassId,
    FabricFormation formation,
    BigDecimal gsmFrom,
    BigDecimal gsmTo,
    GsmBasis gsmBasis) {

  /** numeric(7, 1): six digits before the point, one after. */
  private static final int MAX_GSM_INTEGER_DIGITS = 6;

  private static final int GSM_SCALE = 1;

  public PriceScope {
    billingBasis = billingBasis == null ? FinishingBillingBasis.RETURNED : billingBasis;
    boolean hasRange = gsmFrom != null || gsmTo != null;
    if (hasRange && gsmBasis == null) {
      throw new FabricDomainException(
          "A weight range needs to say whether it is the greige or the finished weight");
    }
    if (!hasRange && gsmBasis != null) {
      throw new FabricDomainException("A weight basis needs a weight range");
    }
    gsmFrom = gsm(gsmFrom, "from");
    gsmTo = gsm(gsmTo, "to");
    if (gsmTo != null && gsmTo.signum() == 0) {
      throw new FabricDomainException("A weight range ends above zero");
    }
    if (gsmFrom != null && gsmTo != null && gsmFrom.compareTo(gsmTo) >= 0) {
      throw new FabricDomainException("A weight range starts below the weight it ends at");
    }
  }

  /** The price for any lot at {@code partnerId}, narrowed by nothing. */
  public static PriceScope plain(UUID partnerId) {
    return new PriceScope(partnerId, null, null, null, null, null, null, null);
  }

  public boolean hasGsmRange() {
    return gsmBasis != null;
  }

  private static BigDecimal gsm(BigDecimal value, String end) {
    if (value == null) {
      return null;
    }
    BigDecimal trimmed = value.stripTrailingZeros();
    if (value.signum() < 0
        || trimmed.scale() > GSM_SCALE
        || trimmed.precision() - trimmed.scale() > MAX_GSM_INTEGER_DIGITS) {
      throw new FabricDomainException(
          "A weight (" + end + ") is zero or more, with at most one decimal: " + value);
    }
    return value.setScale(GSM_SCALE);
  }
}
