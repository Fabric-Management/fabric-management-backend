package com.fabricmanagement.procurement.quote.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "supplier_quote_line", schema = "procurement")
@Getter
@Setter
@NoArgsConstructor
public class SupplierQuoteLine extends BaseEntity {

  @Column(name = "supplier_quote_id", nullable = false)
  private UUID supplierQuoteId;

  @Column(name = "rfq_line_id", nullable = false)
  private UUID rfqLineId;

  @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
  private BigDecimal unitPrice;

  @Column(name = "currency", nullable = false, length = 10)
  private String currency;

  @Column(name = "qty", nullable = false, precision = 15, scale = 3)
  private BigDecimal qty;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Type(JsonType.class)
  @Column(name = "volume_discounts", columnDefinition = "jsonb", nullable = false)
  private String volumeDiscounts = "{}";

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  public void markAsDeleted() {
    this.isActive = false;
    super.delete();
  }

  @Override
  public String getModuleCode() {
    return "PROCUREMENT";
  }
}
