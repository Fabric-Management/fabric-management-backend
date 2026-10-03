package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * How much of a line goes in a delivery, in the line's unit. The allocations of a line never exceed
 * its quantity; quantity not yet allocated stays visible as unallocated, never assumed to go in a
 * particular delivery.
 */
@Entity
@Table(name = "order_line_allocation", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderLineAllocation extends BaseEntity {

  public static final int QUANTITY_SCALE = 3;

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Column(name = "line_id", nullable = false, updatable = false)
  private UUID lineId;

  @Column(name = "delivery_id", nullable = false, updatable = false)
  private UUID deliveryId;

  @Column(name = "quantity", nullable = false, precision = 15, scale = QUANTITY_SCALE)
  private BigDecimal quantity;

  public static OrderLineAllocation of(
      UUID salesOrderId, UUID lineId, UUID deliveryId, BigDecimal quantity) {
    if (salesOrderId == null || lineId == null || deliveryId == null) {
      throw new IllegalArgumentException("Order, line and delivery are required");
    }
    if (quantity == null || quantity.signum() <= 0) {
      throw new OrderDomainException("An allocated quantity is greater than zero");
    }
    if (quantity.stripTrailingZeros().scale() > QUANTITY_SCALE) {
      throw new OrderDomainException("Quantities have at most three decimals");
    }
    OrderLineAllocation value = new OrderLineAllocation();
    value.salesOrderId = salesOrderId;
    value.lineId = lineId;
    value.deliveryId = deliveryId;
    value.quantity = quantity;
    return value;
  }

  /**
   * A line's quantity or unit may change only if its deliveries still fit: the unit is fixed while
   * any quantity is allocated (the allocations are in that unit), and the quantity never drops
   * below what the deliveries carry. Allocations are corrected first, explicitly.
   */
  public static void assertLineChangeFits(
      BigDecimal allocated, String currentUnit, BigDecimal quantity, String unit) {
    if (allocated == null || allocated.signum() == 0) {
      return;
    }
    if (!sameUnit(currentUnit, unit)) {
      throw OrderDomainException.rule(
          "LINE_UNIT_LOCKED_BY_ALLOCATION",
          "Deliveries carry this line in "
              + currentUnit
              + ": remove its allocations before changing the unit");
    }
    if (quantity == null || quantity.compareTo(allocated) < 0) {
      throw OrderDomainException.rule(
          "LINE_QUANTITY_BELOW_ALLOCATED",
          "Deliveries carry "
              + allocated.stripTrailingZeros().toPlainString()
              + " "
              + currentUnit
              + " of this line: reduce their allocations first");
    }
  }

  private static boolean sameUnit(String left, String right) {
    String a = Text.trimmed(left);
    String b = Text.trimmed(right);
    return a == null ? b == null : a.equalsIgnoreCase(b == null ? "" : b);
  }

  @Override
  protected String getModuleCode() {
    return "SLA";
  }
}
