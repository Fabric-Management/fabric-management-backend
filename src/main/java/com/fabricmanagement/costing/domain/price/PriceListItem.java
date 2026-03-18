package com.fabricmanagement.costing.domain.price;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;

/**
 * A single price row inside a {@link PriceList}.
 *
 * <p>Can be general (tradingPartnerId = null) or supplier-specific (tradingPartnerId set). The
 * system first looks for a supplier-specific price; if not found, falls back to the general price.
 * Volume-based discounts are stored in {@link VolumePriceBreak}.
 *
 * <p>When a GoodsReceipt is CONFIRMED, the actual purchase price is written back to the
 * corresponding supplier-specific PriceListItem so future calculations reflect real costs.
 */
@Entity
@Table(name = "price_list_item", schema = "costing")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceListItem extends BaseEntity {

  @Column(name = "price_list_id", nullable = false)
  private UUID priceListId;

  /** Soft reference to {@code costing.cost_item.code}. */
  @Column(name = "cost_item_code", nullable = false, length = 50)
  private String costItemCode;

  /** Null = applies to all materials; non-null = material-specific price. */
  @Column(name = "material_id")
  private UUID materialId;

  /** Null = general price; non-null = contracted supplier price. */
  @Column(name = "trading_partner_id")
  private UUID tradingPartnerId;

  @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
  private BigDecimal unitPrice;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Column(name = "currency", nullable = false, length = 10)
  @Builder.Default
  private String currency = "TRY";

  /** Volume-based price breaks; loaded lazily only when needed for calculation. */
  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  @JoinColumn(name = "price_list_item_id")
  @Builder.Default
  private List<VolumePriceBreak> volumeBreaks = new ArrayList<>();

  /**
   * Resolve the effective unit price for a given quantity, applying volume discounts if available.
   *
   * @param qty the order or consumption quantity
   * @return the applicable unit price (with or without volume discount)
   */
  public BigDecimal resolveUnitPrice(BigDecimal qty) {
    return volumeBreaks.stream()
        .filter(vb -> vb.appliesToQuantity(qty))
        .findFirst()
        .map(VolumePriceBreak::getUnitPrice)
        .orElse(unitPrice);
  }

  @Override
  protected String getModuleCode() {
    return "PLITEM";
  }
}
