package com.fabricmanagement.finance.invoice.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
    name = "finance_invoice_line",
    schema = "finance",
    indexes = {
      @Index(name = "idx_invl_invoice", columnList = "invoice_id"),
      @Index(name = "idx_invl_tenant", columnList = "tenant_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceLine extends BaseEntity {

  @Column(name = "invoice_id", nullable = false, insertable = false, updatable = false)
  private UUID invoiceId;

  @Column(name = "line_number", nullable = false)
  private Integer lineNumber;

  @Column(name = "description", nullable = false, length = 500)
  private String description;

  @Column(name = "product_code", length = 50)
  private String productCode;

  @Column(name = "unit", length = 20)
  @Builder.Default
  private String unit = "PCS";

  @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
  private BigDecimal quantity;

  @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
  private BigDecimal unitPrice;

  @Column(name = "discount_rate", precision = 5, scale = 2)
  @Builder.Default
  private BigDecimal discountRate = BigDecimal.ZERO;

  @Column(name = "tax_rate", precision = 5, scale = 2)
  @Builder.Default
  private BigDecimal taxRate = BigDecimal.ZERO;

  @Column(name = "line_subtotal", nullable = false, precision = 19, scale = 4)
  private BigDecimal lineSubtotal;

  @Column(name = "line_tax", precision = 19, scale = 4)
  @Builder.Default
  private BigDecimal lineTax = BigDecimal.ZERO;

  @Column(name = "line_discount", precision = 19, scale = 4)
  @Builder.Default
  private BigDecimal lineDiscount = BigDecimal.ZERO;

  @Column(name = "line_total", nullable = false, precision = 19, scale = 4)
  private BigDecimal lineTotal;

  @Column(name = "notes", length = 500)
  private String notes;

  @Override
  protected String getModuleCode() {
    return "INVL";
  }

  public void calculate() {
    this.lineSubtotal = this.quantity.multiply(this.unitPrice).setScale(4, RoundingMode.HALF_UP);

    this.lineDiscount =
        this.discountRate != null && this.discountRate.compareTo(BigDecimal.ZERO) > 0
            ? this.lineSubtotal
                .multiply(this.discountRate)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

    BigDecimal afterDiscount = this.lineSubtotal.subtract(this.lineDiscount);

    this.lineTax =
        this.taxRate != null && this.taxRate.compareTo(BigDecimal.ZERO) > 0
            ? afterDiscount
                .multiply(this.taxRate)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

    this.lineTotal = afterDiscount.add(this.lineTax);
  }

  @PrePersist
  @PreUpdate
  protected void onLineChange() {
    calculate();
  }

  public BigDecimal getTaxRate() {
    return this.taxRate;
  }
}
