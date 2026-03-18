package com.fabricmanagement.sales.pricing.app;

import com.fabricmanagement.sales.pricing.domain.DiscountPolicy;
import com.fabricmanagement.sales.quote.domain.QuotePriceZone;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Service;

/**
 * Stateless pricing rules engine.
 *
 * <p>Evaluates offered price against catalog list price and estimated production cost to:
 *
 * <ol>
 *   <li>Compute the discount rate and profit margin.
 *   <li>Classify the price into a {@link QuotePriceZone} using sequential rule evaluation:
 *       <ul>
 *         <li><b>BLOCKED</b> — profitMargin &le; DiscountPolicy.minProfitMargin (red line)
 *         <li><b>MANAGER_APPROVAL</b> — discountRate &gt; DiscountPolicy.requireManagerAbove
 *         <li><b>FREE</b> — passed all checks; no approval required
 *       </ul>
 * </ol>
 */
@Service
public class PricingEngineService {

  /** Return record containing the calculated results from the Pricing Engine. */
  @Getter
  @Builder
  public static class PricingResult {
    private final BigDecimal discountRate;
    private final BigDecimal profitMargin;
    private final QuotePriceZone priceZone;
  }

  public PricingResult evaluatePrice(
      BigDecimal listPrice,
      BigDecimal offeredPrice,
      BigDecimal estimatedCost,
      DiscountPolicy policy) {

    // 1. Discount Rate: (listPrice - offeredPrice) / listPrice
    BigDecimal discountRate = BigDecimal.ZERO;
    if (listPrice != null && listPrice.compareTo(BigDecimal.ZERO) > 0) {
      discountRate = listPrice.subtract(offeredPrice).divide(listPrice, 4, RoundingMode.HALF_UP);
    }

    // 2. Profit Margin: (offeredPrice - estimatedCost) / offeredPrice
    //    If cost is unknown, default to 100% margin so the quote is not blocked
    //    solely due to missing cost data. This is a deliberate business policy —
    //    revisit if cost data should be mandatory before quoting.
    BigDecimal profitMargin = BigDecimal.ZERO;
    if (offeredPrice != null && offeredPrice.compareTo(BigDecimal.ZERO) > 0) {
      if (estimatedCost == null) {
        profitMargin = BigDecimal.ONE; // 100% assumed margin — see comment above
      } else {
        profitMargin =
            offeredPrice.subtract(estimatedCost).divide(offeredPrice, 4, RoundingMode.HALF_UP);
      }
    }

    // 3. Sequential Rule Evaluation (order matters — most restrictive first)
    QuotePriceZone zone;

    // RULE 1 — Red Line / BLOCKED: profit is below minimum acceptable
    if (profitMargin.compareTo(policy.getMinProfitMargin()) <= 0) {
      zone = QuotePriceZone.BLOCKED;
    }
    // RULE 2 — MANAGER_APPROVAL: discount exceeds the manager approval threshold
    //   Uses requireManagerAbove, which is the explicit manager-approval trigger
    //   (distinct from the hard red-line minProfitMargin).
    else if (discountRate.compareTo(policy.getRequireManagerAbove()) > 0) {
      zone = QuotePriceZone.MANAGER_APPROVAL;
    }
    // RULE 3 — FREE zone: within all limits, no approval needed
    else {
      zone = QuotePriceZone.FREE;
    }

    return PricingResult.builder()
        .discountRate(discountRate)
        .profitMargin(profitMargin)
        .priceZone(zone)
        .build();
  }
}
