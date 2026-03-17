package com.fabricmanagement.costing.domain.price;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

/**
 * Quantity-tiered price override for a {@link PriceListItem}.
 *
 * <p>When the ordered quantity falls within {@code [minQty, maxQty)}, the {@code unitPrice} (or
 * {@code discountRate} applied to the base price) overrides the parent item's base price.
 *
 * <p>A null {@code maxQty} means the break applies for all quantities ≥ {@code minQty}.
 */
@Entity
@Table(name = "volume_price_break", schema = "costing")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VolumePriceBreak extends BaseEntity {

  @Column(name = "price_list_item_id", nullable = false)
  private UUID priceListItemId;

  @Column(name = "min_qty", nullable = false, precision = 15, scale = 3)
  private BigDecimal minQty;

  /** Null = open-ended (≥ minQty). */
  @Column(name = "max_qty", precision = 15, scale = 3)
  private BigDecimal maxQty;

  /** Explicit unit price for this tier (used when discountRate is null). */
  @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
  private BigDecimal unitPrice;

  /**
   * Optional fractional discount off the parent item's base price (0.0000–1.0000). When set, the
   * effective price = base * (1 - discountRate).
   */
  @Column(name = "discount_rate", precision = 5, scale = 4)
  private BigDecimal discountRate;

  /** Returns true when this break covers the supplied quantity. */
  public boolean appliesToQuantity(BigDecimal qty) {
    if (qty.compareTo(minQty) < 0) return false;
    return maxQty == null || qty.compareTo(maxQty) < 0;
  }

  @Override
  protected String getModuleCode() {
    return "VPBRK";
  }
}
