package com.fabricmanagement.sales.app;

import static org.junit.jupiter.api.Assertions.*;

import com.fabricmanagement.sales.pricing.app.PricingEngineService;
import com.fabricmanagement.sales.pricing.app.PricingEngineService.PricingResult;
import com.fabricmanagement.sales.pricing.domain.DiscountPolicy;
import com.fabricmanagement.sales.quote.domain.QuotePriceZone;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PricingEngineServiceTest {

  private PricingEngineService pricingEngineService;
  private DiscountPolicy mockPolicy;

  @BeforeEach
  void setUp() {
    pricingEngineService = new PricingEngineService();
    mockPolicy = new DiscountPolicy();
    // baseDiscountLimit: internal reference, no longer used for zone evaluation
    mockPolicy.setBaseDiscountLimit(new BigDecimal("0.1000")); // 10%
    // requireManagerAbove: the actual threshold triggering MANAGER_APPROVAL zone
    mockPolicy.setRequireManagerAbove(new BigDecimal("0.1000")); // 10%
    mockPolicy.setMinProfitMargin(new BigDecimal("0.0500")); // 5% Red Line
  }

  @Test
  @DisplayName("Should return FREE zone when discount is low and profit margin is high")
  void testFreeZone() {
    BigDecimal listPrice = new BigDecimal("100.00");
    BigDecimal offeredPrice = new BigDecimal("95.00"); // 5% discount
    BigDecimal estimatedCost = new BigDecimal("50.00"); // Profit = (95-50)/95 = 47%

    PricingResult result =
        pricingEngineService.evaluatePrice(listPrice, offeredPrice, estimatedCost, mockPolicy);

    assertEquals(new BigDecimal("0.0500"), result.getDiscountRate());
    assertEquals(new BigDecimal("0.4737"), result.getProfitMargin()); // 45 / 95 = 0.47368...
    assertEquals(QuotePriceZone.FREE, result.getPriceZone());
  }

  @Test
  @DisplayName("Should return MANAGER_APPROVAL zone when discount exceeds limit but profit is safe")
  void testManagerApprovalZone() {
    BigDecimal listPrice = new BigDecimal("100.00");
    BigDecimal offeredPrice = new BigDecimal("85.00"); // 15% discount (> 10%)
    BigDecimal estimatedCost = new BigDecimal("50.00"); // Profit = (85-50)/85 = 41% (> 5%)

    PricingResult result =
        pricingEngineService.evaluatePrice(listPrice, offeredPrice, estimatedCost, mockPolicy);

    assertEquals(new BigDecimal("0.1500"), result.getDiscountRate());
    assertEquals(new BigDecimal("0.4118"), result.getProfitMargin());
    assertEquals(QuotePriceZone.MANAGER_APPROVAL, result.getPriceZone());
  }

  @Test
  @DisplayName("Should return BLOCKED zone when profit margin is below the minimum red line")
  void testBlockedZone() {
    BigDecimal listPrice = new BigDecimal("100.00");
    // Even if we don't apply a discount, what if the cost is extremely high?
    BigDecimal offeredPrice = new BigDecimal("100.00"); // 0% discount
    BigDecimal estimatedCost = new BigDecimal("96.00"); // Profit = (100-96)/100 = 4% (< 5%)

    PricingResult result =
        pricingEngineService.evaluatePrice(listPrice, offeredPrice, estimatedCost, mockPolicy);

    assertEquals(new BigDecimal("0.0000"), result.getDiscountRate());
    assertEquals(new BigDecimal("0.0400"), result.getProfitMargin());
    assertEquals(QuotePriceZone.BLOCKED, result.getPriceZone());
  }

  @Test
  @DisplayName("Blocked Rule should override Manager Approval Rule")
  void testBlockedOverridesManagerApproval() {
    BigDecimal listPrice = new BigDecimal("100.00");
    BigDecimal offeredPrice = new BigDecimal("85.00"); // 15% discount (triggers MANAGER_APPROVAL)
    BigDecimal estimatedCost =
        new BigDecimal("82.00"); // Profit = (85-82)/85 = 3.5% (triggers BLOCKED)

    PricingResult result =
        pricingEngineService.evaluatePrice(listPrice, offeredPrice, estimatedCost, mockPolicy);

    assertEquals(new BigDecimal("0.1500"), result.getDiscountRate());
    assertEquals(new BigDecimal("0.0353"), result.getProfitMargin()); // 3 / 85 = 0.03529...
    assertEquals(
        QuotePriceZone.BLOCKED,
        result.getPriceZone(),
        "The BLOCKED (Red Line) rule must take precedence over MANAGER_APPROVAL");
  }

  @Test
  @DisplayName(
      "Should handle missing cost gracefully (e.g. assume 100% profit, rely on discount limit)")
  void testMissingCost() {
    BigDecimal listPrice = new BigDecimal("100.00");
    BigDecimal offeredPrice = new BigDecimal("85.00"); // 15% discount (> 10%)
    BigDecimal estimatedCost = null;

    PricingResult result =
        pricingEngineService.evaluatePrice(listPrice, offeredPrice, estimatedCost, mockPolicy);

    assertEquals(new BigDecimal("0.1500"), result.getDiscountRate());
    assertEquals(BigDecimal.ONE, result.getProfitMargin()); // Assumed 100%
    assertEquals(QuotePriceZone.MANAGER_APPROVAL, result.getPriceZone());
  }
}
