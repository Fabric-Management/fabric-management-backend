package com.fabricmanagement.procurement.purchaseorder.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.procurement.common.exception.ProcurementDomainException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PurchaseOrderLineTest {

  // ── totalPrice recalculation ────────────────────────────────────────────────

  @Nested
  class RecalculateTotal {

    @Test
    void shouldRecalculateTotalPriceWhenQtyChanges() {
      PurchaseOrderLine line = PurchaseOrderLine.builder().build();
      line.setQty(new BigDecimal("10.000"));
      line.setUnitPrice(Money.of(5.5000, "TRY"));

      line.setQty(new BigDecimal("20.000"));

      assertThat(line.getTotalPrice()).isEqualByComparingTo("110.000");
    }

    @Test
    void shouldRecalculateTotalPriceWhenUnitPriceChanges() {
      PurchaseOrderLine line = PurchaseOrderLine.builder().build();
      line.setQty(new BigDecimal("10.000"));
      line.setUnitPrice(Money.of(5.5000, "TRY"));

      line.setUnitPrice(Money.of(6.0000, "TRY"));

      assertThat(line.getTotalPrice()).isEqualByComparingTo("60.000");
    }

    @Test
    void shouldNotRecalculateWhenQtyIsNull() {
      PurchaseOrderLine line = PurchaseOrderLine.builder().build();
      line.setUnitPrice(Money.of(5.5000, "TRY"));

      assertThat(line.getTotalPrice()).isNull();
    }

    @Test
    void shouldNotRecalculateWhenUnitPriceIsNull() {
      PurchaseOrderLine line = PurchaseOrderLine.builder().build();
      line.setQty(new BigDecimal("10.000"));

      assertThat(line.getTotalPrice()).isNull();
    }

    @Test
    void shouldRoundTotalPriceToScale3() {
      PurchaseOrderLine line = PurchaseOrderLine.builder().build();
      // TRY has 2 decimal places, so Money.of(3.3333, "TRY") becomes 3.33
      // 3.333 × 3.33 = 11.09889 → rounded to scale 3 → 11.099
      line.setQty(new BigDecimal("3.333"));
      line.setUnitPrice(Money.of(3.3333, "TRY"));

      assertThat(line.getTotalPrice()).isEqualByComparingTo("11.099");
      assertThat(line.getTotalPrice().scale()).isEqualTo(3);
    }

    @Test
    void shouldRoundTotalPriceToScale3_withActualRounding() {
      PurchaseOrderLine line = PurchaseOrderLine.builder().build();
      // 7.777 × 1.29 = 10.03233 → rounded to scale 3 → 10.032
      line.setQty(new BigDecimal("7.777"));
      line.setUnitPrice(Money.of(1.29, "TRY"));

      assertThat(line.getTotalPrice()).isEqualByComparingTo("10.032");
      assertThat(line.getTotalPrice().scale()).isEqualTo(3);
    }
  }

  // ── factory method & builder recalculation ──────────────────────────────────

  @Nested
  class Construction {

    @Test
    void factoryMethodShouldRecalculateTotal() {
      PurchaseOrderLine line =
          PurchaseOrderLine.create(
              UUID.randomUUID(),
              null,
              null,
              "Test product",
              new BigDecimal("10.000"),
              "KG",
              Money.of(5.50, "TRY"),
              null);

      assertThat(line.getTotalPrice()).isEqualByComparingTo("55.000");
    }

    @Test
    void builderShouldRecalculateTotal() {
      PurchaseOrderLine line =
          PurchaseOrderLine.builder()
              .purchaseOrderId(UUID.randomUUID())
              .productDesc("Test")
              .qty(new BigDecimal("10.000"))
              .unit("KG")
              .unitPrice(Money.of(5.50, "TRY"))
              .build();

      assertThat(line.getTotalPrice()).isEqualByComparingTo("55.000");
    }
  }

  // ── product validation ──────────────────────────────────────────────────────

  @Nested
  class ProductValidation {

    @Test
    void shouldRejectWhenBothProductIdAndProductDescAreNull() {
      PurchaseOrderLine line =
          PurchaseOrderLine.builder()
              .qty(new BigDecimal("10"))
              .unitPrice(Money.of(5, "USD"))
              .build();

      assertThatThrownBy(
              () -> {
                var method = PurchaseOrderLine.class.getDeclaredMethod("validateEntity");
                method.setAccessible(true);
                method.invoke(line);
              })
          .hasCauseInstanceOf(ProcurementDomainException.class);
    }
  }
}
