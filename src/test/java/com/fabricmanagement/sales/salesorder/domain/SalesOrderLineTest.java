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

  private SalesOrderLine lineWithRequestedQty(BigDecimal requestedQty) {
    return SalesOrderLine.builder()
        .productDesc("Cotton fabric")
        .requestedQty(requestedQty)
        .unit("KG")
        .build();
  }
}
