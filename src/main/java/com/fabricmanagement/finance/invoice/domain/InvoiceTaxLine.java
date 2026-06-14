package com.fabricmanagement.finance.invoice.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "finance_invoice_tax_line", schema = "finance")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InvoiceTaxLine extends BaseEntity {

  @Column(name = "invoice_id", nullable = false, insertable = false, updatable = false)
  private UUID invoiceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "tax_category", nullable = false, length = 20)
  @Builder.Default
  private TaxCategory taxCategory = TaxCategory.STANDARD;

  @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
  @Builder.Default
  private BigDecimal taxRate = BigDecimal.ZERO;

  @Column(name = "taxable_base", nullable = false, precision = 19, scale = 4)
  @Builder.Default
  private BigDecimal taxableBase = BigDecimal.ZERO;

  @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
  @Builder.Default
  private BigDecimal taxAmount = BigDecimal.ZERO;

  @Override
  protected String getModuleCode() {
    return "ITL";
  }
}
