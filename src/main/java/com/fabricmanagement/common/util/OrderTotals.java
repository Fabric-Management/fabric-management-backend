package com.fabricmanagement.common.util;

import com.fabricmanagement.common.infrastructure.web.exception.CurrencyMismatchException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Value object grouping related totals for an order to ensure a consistent currency across them
 * all.
 *
 * <p>Stores amounts as raw {@link BigDecimal} columns and reconstructs {@link Money} objects via
 * getters. This avoids the {@code insertable=false, updatable=false} problem with multiple
 * {@code @Embedded Money} fields sharing the same currency.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderTotals {

  @Column(name = "currency", length = 3, nullable = false)
  private String currency;

  @Column(name = "total_amount", precision = 18, scale = 2)
  private BigDecimal totalAmountValue;

  @Column(name = "tax_amount", precision = 18, scale = 2)
  private BigDecimal taxAmountValue;

  @Column(name = "discount_amount", precision = 18, scale = 2)
  private BigDecimal discountAmountValue;

  private OrderTotals(
      String currency, BigDecimal totalAmount, BigDecimal taxAmount, BigDecimal discountAmount) {
    if (currency == null) {
      throw new IllegalArgumentException("Currency cannot be null");
    }
    this.currency = currency;
    this.totalAmountValue = totalAmount;
    this.taxAmountValue = taxAmount;
    this.discountAmountValue = discountAmount;
  }

  public static OrderTotals zero(String currencyCode) {
    return new OrderTotals(currencyCode, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
  }

  public static OrderTotals of(Money totalAmount, Money taxAmount, Money discountAmount) {
    if (totalAmount == null || taxAmount == null || discountAmount == null) {
      throw new IllegalArgumentException("Amounts cannot be null");
    }
    String currency = totalAmount.getCurrency().getCurrencyCode();
    if (!taxAmount.getCurrency().getCurrencyCode().equals(currency)
        || !discountAmount.getCurrency().getCurrencyCode().equals(currency)) {
      throw new CurrencyMismatchException(currency, "Mixed currencies in OrderTotals.of()");
    }
    return new OrderTotals(
        currency, totalAmount.getAmount(), taxAmount.getAmount(), discountAmount.getAmount());
  }

  /** Reconstructs a {@link Money} from the stored total amount, null-safe. */
  public Money getTotalAmount() {
    return totalAmountValue != null ? Money.of(totalAmountValue, currency) : Money.zero(currency);
  }

  /** Reconstructs a {@link Money} from the stored tax amount, null-safe. */
  public Money getTaxAmount() {
    return taxAmountValue != null ? Money.of(taxAmountValue, currency) : Money.zero(currency);
  }

  /** Reconstructs a {@link Money} from the stored discount amount, null-safe. */
  public Money getDiscountAmount() {
    return discountAmountValue != null
        ? Money.of(discountAmountValue, currency)
        : Money.zero(currency);
  }

  public OrderTotals withTotalAmount(Money totalAmount) {
    requireCurrency(totalAmount);
    return new OrderTotals(
        this.currency, totalAmount.getAmount(), this.taxAmountValue, this.discountAmountValue);
  }

  public OrderTotals withTaxAmount(Money taxAmount) {
    requireCurrency(taxAmount);
    return new OrderTotals(
        this.currency, this.totalAmountValue, taxAmount.getAmount(), this.discountAmountValue);
  }

  public OrderTotals withDiscountAmount(Money discountAmount) {
    requireCurrency(discountAmount);
    return new OrderTotals(
        this.currency, this.totalAmountValue, this.taxAmountValue, discountAmount.getAmount());
  }

  public Money calculateGrandTotal() {
    return getTotalAmount().add(getTaxAmount()).subtract(getDiscountAmount());
  }

  /** Recalculates the net total: totalAmount - discountAmount. Null-safe. */
  public Money calculateNetTotal() {
    return getTotalAmount().subtract(getDiscountAmount());
  }

  private void requireCurrency(Money money) {
    if (money != null && !money.getCurrency().getCurrencyCode().equals(this.currency)) {
      throw new CurrencyMismatchException(this.currency, money.getCurrency().getCurrencyCode());
    }
  }
}
