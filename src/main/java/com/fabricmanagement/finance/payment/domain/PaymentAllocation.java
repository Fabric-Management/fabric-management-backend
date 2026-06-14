package com.fabricmanagement.finance.payment.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.util.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "finance_payment_allocation",
    schema = "finance",
    indexes = {
      @Index(name = "idx_paya_tenant", columnList = "tenant_id"),
      @Index(name = "idx_paya_payment", columnList = "payment_id"),
      @Index(name = "idx_paya_invoice", columnList = "invoice_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PaymentAllocation extends BaseEntity {

  @Column(name = "payment_id", nullable = false, insertable = false, updatable = false)
  private UUID paymentId;

  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId;

  @Embedded
  @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false))
  @AttributeOverride(name = "currency", column = @Column(name = "currency", nullable = false))
  private Money amount;

  @Column(name = "allocated_at", nullable = false)
  @Builder.Default
  private Instant allocatedAt = Instant.now();

  @Override
  protected String getModuleCode() {
    return "PAYA";
  }
}
