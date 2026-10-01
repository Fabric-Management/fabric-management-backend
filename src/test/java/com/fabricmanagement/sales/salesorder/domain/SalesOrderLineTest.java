package com.fabricmanagement.sales.salesorder.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.sales.common.exception.OrderDomainException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SalesOrderLineTest {

  @Test
  void validateEntity_whenActiveAndRequestedQtyNullThrowsDomainException() {
    SalesOrderLine line = lineWithRequestedQty(null);

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(line, "validateEntity"))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("Requested quantity must be greater than zero");
  }

  @Test
  void validateEntity_whenActiveAndRequestedQtyZeroOrNegativeThrowsDomainException() {
    SalesOrderLine zeroQty = lineWithRequestedQty(BigDecimal.ZERO);
    SalesOrderLine negativeQty = lineWithRequestedQty(new BigDecimal("-1"));

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(zeroQty, "validateEntity"))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("Requested quantity must be greater than zero");
    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(negativeQty, "validateEntity"))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("Requested quantity must be greater than zero");
  }

  @Test
  void validateEntity_whenInactiveAllowsLegacyInvalidRequestedQtyForSoftDelete() {
    SalesOrderLine line = lineWithRequestedQty(null);
    line.delete();

    assertThatCode(() -> ReflectionTestUtils.invokeMethod(line, "validateEntity"))
        .doesNotThrowAnyException();
  }

  @Test
  void getRemainingQty_returnsRequestedMinusShipped() {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .productDesc("Cotton fabric")
            .requestedQty(new BigDecimal("100"))
            .shippedQty(new BigDecimal("35"))
            .build();

    assertThat(line.getRemainingQty()).isEqualByComparingTo("65");
  }

  @Test
  void addShippedQuantity_whenOverShippedUpdatesQuantityAndReturnsTrueWithoutThrowing() {
    UUID shipmentLineId = UUID.randomUUID();
    SalesOrderLine line =
        SalesOrderLine.builder()
            .productDesc("Cotton fabric")
            .requestedQty(new BigDecimal("100"))
            .shippedQty(new BigDecimal("80"))
            .build();

    boolean applied = line.addShippedQuantity(shipmentLineId, new BigDecimal("30"));

    assertThat(applied).isTrue();
    assertThat(line.getShippedQty()).isEqualByComparingTo("110");
    assertThat(line.getRemainingQty()).isEqualByComparingTo("-10");
    assertThat(line.isOverShipped()).isTrue();
  }

  @Test
  void addShippedQuantity_whenSameShipmentLineIdIsRepeatedIsNoop() {
    UUID shipmentLineId = UUID.randomUUID();
    SalesOrderLine line =
        SalesOrderLine.builder()
            .productDesc("Cotton fabric")
            .requestedQty(new BigDecimal("100"))
            .shippedQty(BigDecimal.ZERO)
            .build();

    boolean firstApplied = line.addShippedQuantity(shipmentLineId, new BigDecimal("40"));
    boolean secondApplied = line.addShippedQuantity(shipmentLineId, new BigDecimal("40"));

    assertThat(firstApplied).isTrue();
    assertThat(secondApplied).isFalse();
    assertThat(line.getShippedQty()).isEqualByComparingTo("40");
  }

  @Test
  void markInProduction_whenRecipeAssigned_movesToInProduction() {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .productDesc("Cotton fabric")
            .requestedQty(new BigDecimal("100"))
            .lineStatus(SalesOrderLineStatus.RECIPE_ASSIGNED)
            .build();

    boolean changed = line.markInProduction();

    assertThat(changed).isTrue();
    assertThat(line.getLineStatus()).isEqualTo(SalesOrderLineStatus.IN_PRODUCTION);
  }

  @Test
  void markInProduction_whenAlreadyCompletedOrAhead_isNoop() {
    SalesOrderLine completedLine =
        SalesOrderLine.builder()
            .productDesc("Cotton fabric")
            .requestedQty(new BigDecimal("100"))
            .lineStatus(SalesOrderLineStatus.COMPLETED)
            .build();

    boolean changed = completedLine.markInProduction();

    assertThat(changed).isFalse();
    assertThat(completedLine.getLineStatus()).isEqualTo(SalesOrderLineStatus.COMPLETED);
  }

  @Test
  void markInProduction_whenPending_isNoopWithoutThrowing() {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .productDesc("Cotton fabric")
            .requestedQty(new BigDecimal("100"))
            .lineStatus(SalesOrderLineStatus.PENDING)
            .build();

    boolean changed = line.markInProduction();

    assertThat(changed).isFalse();
    assertThat(line.getLineStatus()).isEqualTo(SalesOrderLineStatus.PENDING);
  }

  @Test
  void markCompleted_whenInProduction_movesToCompleted() {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .productDesc("Cotton fabric")
            .requestedQty(new BigDecimal("100"))
            .lineStatus(SalesOrderLineStatus.IN_PRODUCTION)
            .build();

    boolean changed = line.markCompleted();

    assertThat(changed).isTrue();
    assertThat(line.getLineStatus()).isEqualTo(SalesOrderLineStatus.COMPLETED);
  }

  @Test
  void markCompleted_whenNotInProduction_isNoopWithoutThrowing() {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .productDesc("Cotton fabric")
            .requestedQty(new BigDecimal("100"))
            .lineStatus(SalesOrderLineStatus.RECIPE_ASSIGNED)
            .build();

    boolean changed = line.markCompleted();

    assertThat(changed).isFalse();
    assertThat(line.getLineStatus()).isEqualTo(SalesOrderLineStatus.RECIPE_ASSIGNED);
  }

  @Test
  void isOverShipped_whenRequestedQtyIsNullReturnsFalse() {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .productDesc("Legacy cotton fabric")
            .requestedQty(null)
            .shippedQty(new BigDecimal("120"))
            .build();

    assertThat(line.isOverShipped()).isFalse();
  }

  @Test
  void validateEntity_rejectsALineWithoutProductEvenWithADescription() {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .productDesc("Navy satin like the customer's swatch")
            .requestedQty(new BigDecimal("500"))
            .unit("M")
            .build();

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(line, "validateEntity"))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("must name a product");
  }

  @Test
  void initialRequestIsCapturedOnceAndSurvivesLaterQuantityChanges() {
    SalesOrderLine line = lineWithRequestedQty(new BigDecimal("500"));
    ReflectionTestUtils.invokeMethod(line, "validateEntity");

    line.setRequestedQty(new BigDecimal("514"));
    ReflectionTestUtils.invokeMethod(line, "validateEntity");

    assertThat(line.getInitialRequestedQty()).isEqualByComparingTo("500");
    assertThat(line.getRequestedQty()).isEqualByComparingTo("514");
  }

  @Test
  void validateEntity_requiresFinishedWidthAndUnitTogether() {
    SalesOrderLine line = lineWithRequestedQty(new BigDecimal("500"));
    line.setFinishedWidth(new BigDecimal("160"));

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(line, "validateEntity"))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("Finished width and its unit");
  }

  private SalesOrderLine lineWithRequestedQty(BigDecimal requestedQty) {
    return SalesOrderLine.builder()
        .productId(UUID.randomUUID())
        .productDesc("Cotton fabric")
        .requestedQty(requestedQty)
        .unit("KG")
        .build();
  }

  @Test
  void pricing_keepsTheAgreedCurrencyBeforeThePriceIsAgreed() {
    SalesOrderLine line = lineWithRequestedQty(new BigDecimal("100"));

    line.updatePricing("USD", null, null, null);

    assertThat(line.getCurrency()).isEqualTo("USD");
    assertThat(line.getUnitPrice()).isNull();
  }

  @Test
  void pricing_rejectsAPriceWithoutCurrencyAndAdjustmentsWithoutPrice() {
    SalesOrderLine line = lineWithRequestedQty(new BigDecimal("100"));

    assertThatThrownBy(() -> line.updatePricing(null, BigDecimal.TEN, null, null))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("must name its currency");
    assertThatThrownBy(() -> line.updatePricing("EUR", null, null, BigDecimal.ONE))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("need an agreed unit price");
    assertThatThrownBy(() -> line.updatePricing("EUR", BigDecimal.TEN, new BigDecimal("-1"), null))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("cannot be negative");
  }

  @Test
  void pricing_discountMustFitTheLineAmountAlsoAfterAQuantityChange() {
    SalesOrderLine line = lineWithRequestedQty(new BigDecimal("100"));
    line.updatePricing("EUR", new BigDecimal("2.00"), new BigDecimal("150"), new BigDecimal("10"));
    assertThat(line.getDiscountAmount().getAmount()).isEqualByComparingTo("150");
    assertThat(line.getTaxAmount().getCurrency().getCurrencyCode()).isEqualTo("EUR");

    line.setRequestedQty(new BigDecimal("50"));

    assertThatThrownBy(line::assertAdjustmentsFitQuantity)
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("cannot exceed the line amount");
  }

  @Test
  void clearingThePriceClearsTheAdjustmentsButKeepsTheCurrency() {
    SalesOrderLine line = lineWithRequestedQty(new BigDecimal("100"));
    line.updatePricing("TRY", new BigDecimal("40"), new BigDecimal("100"), new BigDecimal("800"));

    line.updateUnitPrice(null);

    assertThat(line.getCurrency()).isEqualTo("TRY");
    assertThat(line.getDiscountAmount()).isNull();
    assertThat(line.getTaxAmount()).isNull();
  }

  @Test
  void tolerance_keepsItsProvenanceWhenTheSameTermsAreSavedAgain() {
    SalesOrderLine line = lineWithRequestedQty(new BigDecimal("100"));
    UUID first = UUID.randomUUID();
    java.time.Instant at = java.time.Instant.parse("2026-10-01T10:00:00Z");
    line.recordTolerance(new BigDecimal("5"), new BigDecimal("3"), first, at);

    line.recordTolerance(
        new BigDecimal("5.00"), new BigDecimal("3"), UUID.randomUUID(), at.plusSeconds(60));

    assertThat(line.getToleranceRecordedBy()).isEqualTo(first);
    assertThat(line.getToleranceRecordedAt()).isEqualTo(at);
  }

  @Test
  void tolerance_isBetweenZeroAndAHundredAndClearedWithBothLimits() {
    SalesOrderLine line = lineWithRequestedQty(new BigDecimal("100"));

    assertThatThrownBy(
            () ->
                line.recordTolerance(
                    new BigDecimal("101"), null, UUID.randomUUID(), java.time.Instant.now()))
        .isInstanceOf(OrderDomainException.class);

    line.recordTolerance(
        new BigDecimal("5"), new BigDecimal("5"), UUID.randomUUID(), java.time.Instant.now());
    line.recordTolerance(null, null, null, null);
    assertThat(line.getToleranceUpPct()).isNull();
    assertThat(line.getToleranceDownPct()).isNull();
    assertThat(line.getToleranceRecordedBy()).isNull();
  }
}
