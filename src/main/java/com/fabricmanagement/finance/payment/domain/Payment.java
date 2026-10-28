package com.fabricmanagement.finance.payment.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import com.fabricmanagement.finance.payment.domain.exception.PaymentReversalException;
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

  /** Set on a reversal row: the payment it cancels. */
  @Column(name = "reverses_payment_id")
  private UUID reversesPaymentId;

  /** Set on the original once it has been reversed: its single reversal. */
  @Column(name = "reversed_by_payment_id")
  private UUID reversedByPaymentId;

  /** Set on a reversal row: why the payment was cancelled. */
  @Column(name = "reversal_reason", length = 1000)
  private String reversalReason;

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

  @Transient
  public boolean isReversal() {
    return reversesPaymentId != null;
  }

  @Transient
  public boolean isReversed() {
    return reversedByPaymentId != null;
  }

  public PaymentAllocation allocate(
      UUID invoiceId, Money allocationAmount, Money invoiceOpenBalance, String invoiceCurrency) {

    if (this.status == PaymentStatus.VOIDED) {
      throw new FinanceDomainException("Cannot allocate a voided payment");
    }

    if (isReversal() || isReversed()) {
      throw PaymentReversalException.conflict(
          "PAYMENT_REVERSED", "Cannot allocate a reversed payment or a reversal");
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

  /**
   * Cancels a payment that nothing was applied to. A payment with invoice allocations is not
   * rewritten in place: it is reversed instead, so the allocations are released on a dated,
   * traceable posting.
   */
  public void voidPayment() {
    if (this.status == PaymentStatus.VOIDED) {
      throw new FinanceDomainException("Payment is already voided");
    }

    if (isReversal()) {
      throw PaymentReversalException.conflict(
          "PAYMENT_REVERSAL_IMMUTABLE", "A reversal cannot be voided");
    }

    if (isReversed()) {
      throw PaymentReversalException.conflict(
          "PAYMENT_ALREADY_REVERSED", "A reversed payment cannot be voided");
    }

    if (hasActiveAllocation()) {
      throw PaymentReversalException.conflict(
          "PAYMENT_VOID_HAS_ALLOCATIONS",
          "A payment with invoice allocations cannot be voided; reverse it instead");
    }

    this.status = PaymentStatus.VOIDED;
  }

  private boolean hasActiveAllocation() {
    return this.allocations.stream().anyMatch(a -> Boolean.TRUE.equals(a.getIsActive()));
  }

  /** The opposite payment, and the invoice allocations that were released to post it. */
  public record Reversal(Payment payment, List<PaymentAllocation> releasedAllocations) {}

  /**
   * Builds the opposite-direction payment that cancels this one, whatever its method, and releases
   * the invoice allocations it carried so the caller can undo them on the invoices. The payment
   * itself stays as recorded; the caller persists the result and then links it with {@link
   * #markReversedBy(UUID)}.
   */
  public Reversal reverse(String reversalNumber, LocalDate reversalDate, String reason) {
    if (getId() == null) {
      throw new IllegalStateException("Only a persisted payment can be reversed");
    }
    if (this.status == PaymentStatus.VOIDED) {
      throw PaymentReversalException.conflict(
          "PAYMENT_VOIDED", "A voided payment cannot be reversed");
    }
    if (isReversal()) {
      throw PaymentReversalException.conflict(
          "PAYMENT_REVERSAL_NOT_REVERSIBLE", "A reversal cannot itself be reversed");
    }
    if (isReversed()) {
      throw PaymentReversalException.conflict(
          "PAYMENT_ALREADY_REVERSED", "This payment is already reversed");
    }
    if (reversalDate == null) {
      throw PaymentReversalException.invalid("A reversal needs an accounting date");
    }
    if (reason == null || reason.isBlank()) {
      throw PaymentReversalException.invalid("A reversal needs a reason");
    }
    if (reversalDate.isBefore(this.paymentDate)) {
      throw PaymentReversalException.invalid("A reversal cannot precede the payment date");
    }

    List<PaymentAllocation> released = new ArrayList<>();
    for (PaymentAllocation allocation : this.allocations) {
      if (Boolean.TRUE.equals(allocation.getIsActive())) {
        allocation.delete();
        released.add(allocation);
      }
    }

    Payment opposite =
        Payment.builder()
            .tradingPartnerId(this.tradingPartnerId)
            .paymentNumber(reversalNumber)
            .direction(
                this.direction == PaymentDirection.INBOUND
                    ? PaymentDirection.OUTBOUND
                    : PaymentDirection.INBOUND)
            .method(this.method)
            .amount(Money.of(this.amount.getAmount(), getCurrency()))
            .paymentDate(reversalDate)
            .notes(reason.trim())
            .reversesPaymentId(getId())
            .reversalReason(reason.trim())
            .build();
    return new Reversal(opposite, List.copyOf(released));
  }

  public void markReversedBy(UUID reversalPaymentId) {
    if (reversalPaymentId == null) {
      throw new IllegalArgumentException("The reversal payment id is required");
    }
    if (isReversed()) {
      throw PaymentReversalException.conflict(
          "PAYMENT_ALREADY_REVERSED", "This payment is already reversed");
    }
    this.reversedByPaymentId = reversalPaymentId;
  }
}
