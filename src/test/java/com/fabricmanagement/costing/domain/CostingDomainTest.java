package com.fabricmanagement.costing.domain;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.costing.domain.calculation.CostCalculation;
import com.fabricmanagement.costing.domain.calculation.CostCalculationLine;
import com.fabricmanagement.costing.domain.calculation.CostEntityType;
import com.fabricmanagement.costing.domain.calculation.CostStage;
import com.fabricmanagement.costing.domain.currency.ExchangeRateSnapshot;
import com.fabricmanagement.costing.domain.exchange.ExchangeRateSource;
import com.fabricmanagement.costing.domain.item.CalculationBase;
import com.fabricmanagement.costing.domain.item.CostItem;
import com.fabricmanagement.costing.domain.item.CostItemScope;
import com.fabricmanagement.costing.domain.price.PriceList;
import com.fabricmanagement.costing.domain.price.PriceListItem;
import com.fabricmanagement.costing.domain.price.VolumePriceBreak;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for Phase 4 costing domain logic (no Spring context needed). */
class CostingDomainTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  // ===========================================================================
  // PriceList validity
  // ===========================================================================

  @Nested
  @DisplayName("PriceList.isValidOn()")
  class PriceListValidityTest {

    @Test
    void valid_when_date_is_within_range() {
      PriceList pl =
          PriceList.create(
              TENANT_ID,
              "Test List",
              "FIBER",
              "TRY",
              LocalDate.of(2026, 1, 1),
              LocalDate.of(2026, 12, 31),
              null);
      assertThat(pl.isValidOn(LocalDate.of(2026, 6, 1))).isTrue();
    }

    @Test
    void invalid_when_date_before_validFrom() {
      PriceList pl =
          PriceList.create(
              TENANT_ID, "Test List", "FIBER", "TRY", LocalDate.of(2026, 3, 1), null, null);
      assertThat(pl.isValidOn(LocalDate.of(2026, 2, 28))).isFalse();
    }

    @Test
    void valid_when_validUntil_is_null_open_ended() {
      PriceList pl =
          PriceList.create(
              TENANT_ID, "Open List", "FIBER", "TRY", LocalDate.of(2026, 1, 1), null, null);
      assertThat(pl.isValidOn(LocalDate.of(2099, 1, 1))).isTrue();
    }
  }

  // ===========================================================================
  // VolumePriceBreak quantity applicability
  // ===========================================================================

  @Nested
  @DisplayName("VolumePriceBreak.appliesToQuantity()")
  class VolumePriceBreakTest {

    @Test
    void applies_within_range() {
      var vb = new VolumePriceBreak();
      vb.setMinQty(new BigDecimal("100"));
      vb.setMaxQty(new BigDecimal("500"));
      vb.setUnitPrice(new BigDecimal("8.50"));
      assertThat(vb.appliesToQuantity(new BigDecimal("250"))).isTrue();
    }

    @Test
    void does_not_apply_below_min() {
      var vb = new VolumePriceBreak();
      vb.setMinQty(new BigDecimal("100"));
      vb.setMaxQty(new BigDecimal("500"));
      vb.setUnitPrice(new BigDecimal("8.50"));
      assertThat(vb.appliesToQuantity(new BigDecimal("50"))).isFalse();
    }

    @Test
    void applies_on_open_ended_tier() {
      var vb = new VolumePriceBreak();
      vb.setMinQty(new BigDecimal("1000"));
      vb.setMaxQty(null); // open-ended
      vb.setUnitPrice(new BigDecimal("7.00"));
      assertThat(vb.appliesToQuantity(new BigDecimal("5000"))).isTrue();
    }
  }

  // ===========================================================================
  // PriceListItem price resolution
  // ===========================================================================

  @Nested
  @DisplayName("PriceListItem.resolveUnitPrice()")
  class PriceListItemResolveTest {

    private PriceListItem itemWithBreaks() {
      var item = new PriceListItem();
      item.setUnitPrice(new BigDecimal("10.00"));
      item.setCurrency("TRY");

      // Volume break: ≥50 kg → 9.00 TRY/kg
      var vb = new VolumePriceBreak();
      vb.setMinQty(new BigDecimal("50"));
      vb.setMaxQty(null);
      vb.setUnitPrice(new BigDecimal("9.00"));
      item.getVolumeBreaks().add(vb);
      return item;
    }

    @Test
    void returns_base_price_for_small_quantity() {
      PriceListItem item = itemWithBreaks();
      assertThat(item.resolveUnitPrice(new BigDecimal("10"))).isEqualByComparingTo("10.00");
    }

    @Test
    void returns_volume_price_for_large_quantity() {
      PriceListItem item = itemWithBreaks();
      assertThat(item.resolveUnitPrice(new BigDecimal("200"))).isEqualByComparingTo("9.00");
    }
  }

  // ===========================================================================
  // ExchangeRateSnapshot currency conversion
  // ===========================================================================

  @Nested
  @DisplayName("ExchangeRateSnapshot.toBase()")
  class ExchangeRateTest {

    @Test
    void converts_usd_to_try_correctly() {
      var snap =
          ExchangeRateSnapshot.capture(
              TENANT_ID, "TRY", "USD", new BigDecimal("32.50"), ExchangeRateSource.TCMB);
      // 10 USD × 32.50 = 325 TRY
      assertThat(snap.toBase(new BigDecimal("10"))).isEqualByComparingTo("325.0000");
    }
  }

  // ===========================================================================
  // CostCalculation totalCost aggregation
  // ===========================================================================

  @Nested
  @DisplayName("CostCalculation.addLine() + totalCost")
  class CostCalculationAggregationTest {

    @Test
    void total_cost_is_sum_of_lines() {
      var calc =
          CostCalculation.create(
              TENANT_ID,
              CostEntityType.WORK_ORDER,
              UUID.randomUUID(),
              "FIBER",
              CostStage.PLANNED,
              "TRY");

      var line1 = new CostCalculationLine();
      line1.setTotalInBaseCurrency(new BigDecimal("500.00"));

      var line2 = new CostCalculationLine();
      line2.setTotalInBaseCurrency(new BigDecimal("250.50"));

      calc.addLine(line1);
      calc.addLine(line2);

      assertThat(calc.getTotalCost()).isEqualByComparingTo("750.50");
      assertThat(calc.getLines()).hasSize(2);
    }

    @Test
    void variance_ratio_computed_correctly() {
      var calc =
          CostCalculation.create(
              TENANT_ID, CostEntityType.BATCH, UUID.randomUUID(), "FIBER", CostStage.ACTUAL, "TRY");
      var line = new CostCalculationLine();
      line.setTotalInBaseCurrency(new BigDecimal("1200.00"));
      calc.addLine(line);

      // previous stage was 1000, ratio = (1200-1000)/1000 = 0.20
      BigDecimal ratio = calc.varianceRatioVs(new BigDecimal("1000.00"));
      assertThat(ratio).isEqualByComparingTo("0.2000");
    }

    @Test
    void variance_ratio_is_zero_when_previous_is_zero() {
      var calc =
          CostCalculation.create(
              TENANT_ID,
              CostEntityType.BATCH,
              UUID.randomUUID(),
              "FIBER",
              CostStage.ESTIMATED,
              "TRY");
      assertThat(calc.varianceRatioVs(BigDecimal.ZERO)).isEqualByComparingTo("0");
    }
  }

  // ===========================================================================
  // CostItem scope
  // ===========================================================================

  @Test
  @DisplayName("CostItem with GLOBAL scope returns correct module code")
  void cost_item_global_scope() {
    var item = new CostItem();
    item.setCode("RAW_PRODUCT");
    item.setScope(CostItemScope.GLOBAL);
    item.setCalculationBase(CalculationBase.PER_KG);
    assertThat(item.getScope()).isEqualTo(CostItemScope.GLOBAL);
    assertThat(item.getCalculationBase()).isEqualTo(CalculationBase.PER_KG);
  }
}
