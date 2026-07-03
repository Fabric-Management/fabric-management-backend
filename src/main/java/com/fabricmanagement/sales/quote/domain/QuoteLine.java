package com.fabricmanagement.sales.quote.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "quote_line", schema = "sales")
@Getter
@Setter
@NoArgsConstructor
public class QuoteLine extends BaseEntity {

  // No explicit quoteId field, mapped by @JoinColumn in Quote

  @Column(name = "product_id")
  private UUID productId;

  @Column(name = "product_desc", columnDefinition = "TEXT")
  private String productDesc;

  @Column(name = "requested_qty", nullable = false, precision = 15, scale = 3)
  private BigDecimal requestedQty;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Column(name = "list_price", nullable = false, precision = 18, scale = 4)
  private BigDecimal listPrice;

  @Column(name = "offered_price", nullable = false, precision = 18, scale = 4)
  private BigDecimal offeredPrice;

  @Column(name = "currency", length = 3)
  private String currency;

  @Column(name = "discount_rate", nullable = false, precision = 5, scale = 4)
  private BigDecimal discountRate;

  @Column(name = "profit_margin", precision = 5, scale = 4)
  private BigDecimal profitMargin;

  @Enumerated(EnumType.STRING)
  @Column(name = "price_zone", nullable = false, length = 30)
  private QuotePriceZone priceZone;

  @Type(JsonType.class)
  @Column(name = "module_specs", columnDefinition = "jsonb", nullable = false)
  private String moduleSpecs = "{}";

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  public BigDecimal lineTotal() {
    if (offeredPrice == null || requestedQty == null) {
      return BigDecimal.ZERO;
    }
    return offeredPrice.multiply(requestedQty);
  }

  public void updateEditableFields(BigDecimal requestedQty, String unit, BigDecimal offeredPrice) {
    this.requestedQty = requestedQty;
    this.unit = unit;
    this.offeredPrice = offeredPrice;
  }

  public void applyPricing(
      BigDecimal discountRate, BigDecimal profitMargin, QuotePriceZone priceZone) {
    this.discountRate = discountRate;
    this.profitMargin = profitMargin;
    this.priceZone = priceZone;
  }

  public void markAsDeleted() {
    this.isActive = false;
    super.delete();
  }

  @Override
  public String getModuleCode() {
    return "SALES";
  }
}
