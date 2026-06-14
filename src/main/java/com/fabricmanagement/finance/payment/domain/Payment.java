package com.fabricmanagement.finance.payment.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

@Entity
@Table(
    name = "finance_payment",
    schema = "finance",
    indexes = {
      @Index(name = "idx_pay_tenant", columnList = "tenant_id"),
      @Index(name = "idx_pay_trading_partner", columnList = "trading_partner_id"),
      @Index(name = "idx_pay_payment_date", columnList = "payment_date"),
      @Index(name = "idx_pay_direction", columnList = "direction")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Payment extends BaseEntity {

  @Column(name = "trading_partner_id", nullable = false)
  private UUID tradingPartnerId;

  @Column(name = "payment_number", nullable = false, length = 50)
  private String paymentNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false, length = 20)
  private PaymentDirection direction;

  @Enumerated(EnumType.STRING)
  @Column(name = "method", nullable = false, length = 30)
  @Builder.Default
  private PaymentMethod method = PaymentMethod.OTHER;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private PaymentStatus status = PaymentStatus.RECEIVED;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "amount",
        column = @Column(name = "amount", nullable = false, precision = 19, scale = 4)),
    @AttributeOverride(name = "currency", column = @Column(name = "currency", length = 3))
  })
  private Money amount;

  @Column(name = "payment_date", nullable = false)
  private LocalDate paymentDate;

  @Column(name = "bank_reference", length = 100)
  private String bankReference;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Type(JsonType.class)
  @Column(name = "metadata", columnDefinition = "jsonb")
  private Map<String, Object> metadata;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "payment_id", nullable = false)
  @Builder.Default
  private List<PaymentAllocation> allocations = new ArrayList<>();

  @Override
  protected String getModuleCode() {
    return "PAY";
  }

  @Transient
  public String getCurrency() {
    return amount.getCurrency().getCurrencyCode();
  }

  @Transient
  public Money getAllocatedAmount() {
    return allocations.stream()
        .filter(PaymentAllocation::getIsActive)
        .map(PaymentAllocation::getAmount)
        .reduce(Money.zero(getCurrency()), Money::add);
  }

  @Transient
  public Money getUnallocatedAmount() {
    return amount.subtract(getAllocatedAmount());
  }

  public PaymentAllocation allocate(
      UUID invoiceId, Money allocationAmount, Money invoiceOpenBalance, String invoiceCurrency) {

    if (this.status == PaymentStatus.VOIDED) {
      throw new FinanceDomainException("Cannot allocate a voided payment");
    }

    if (!getCurrency().equals(invoiceCurrency)) {
      throw new FinanceDomainException("Cross-currency allocation not supported (see FIN-6)");
    }

    if (allocationAmount.isNegative() || allocationAmount.isZero()) {
      throw new FinanceDomainException("Allocation amount must be greater than zero");
    }

    if (allocationAmount.isGreaterThan(getUnallocatedAmount())) {
      throw new FinanceDomainException("Allocation exceeds unallocated payment amount");
    }

    if (allocationAmount.isGreaterThan(invoiceOpenBalance)) {
      throw new FinanceDomainException("Allocation exceeds invoice open balance");
    }

    PaymentAllocation allocation =
        PaymentAllocation.builder().invoiceId(invoiceId).amount(allocationAmount).build();

    this.allocations.add(allocation);
    return allocation;
  }

  public PaymentAllocation deallocate(UUID allocationId) {
    if (this.status == PaymentStatus.VOIDED) {
      throw new FinanceDomainException("Cannot deallocate from a voided payment");
    }

    PaymentAllocation allocation =
        this.allocations.stream()
            .filter(a -> a.getId().equals(allocationId))
            .findFirst()
            .orElseThrow(() -> new FinanceDomainException("Allocation not found"));

    if (!allocation.getIsActive()) {
      throw new FinanceDomainException("Allocation is already inactive/deleted");
    }

    // Soft-deletes the allocation; it remains in the allocations list
    // but is filtered out by getAllocatedAmount()/getUnallocatedAmount()
    allocation.delete();
    return allocation;
  }

  public List<PaymentAllocation> voidPayment() {
    if (this.status == PaymentStatus.VOIDED) {
      throw new FinanceDomainException("Payment is already voided");
    }

    this.status = PaymentStatus.VOIDED;

    List<PaymentAllocation> reversedAllocations = new ArrayList<>();
    for (PaymentAllocation allocation : this.allocations) {
      if (allocation.getIsActive()) {
        allocation.delete();
        reversedAllocations.add(allocation);
      }
    }
    return reversedAllocations;
  }
}
