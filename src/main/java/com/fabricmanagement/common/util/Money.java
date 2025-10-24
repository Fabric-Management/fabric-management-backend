package com.fabricmanagement.common.util;

import lombok.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Value object representing monetary amounts with currency.
 *
 * <p>Immutable, type-safe representation of money that prevents common
 * currency-related bugs and ensures proper decimal precision.</p>
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>Immutable (thread-safe)</li>
 *   <li>Currency-aware operations</li>
 *   <li>Proper decimal precision (scale = 2)</li>
 *   <li>Type-safe arithmetic operations</li>
 *   <li>Prevents adding different currencies</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * Money price = Money.of(100.50, "USD");
 * Money tax = Money.of(18.09, "USD");
 * Money total = price.add(tax);  // 118.59 USD
 *
 * Money discounted = price.multiply(0.9);  // 90.45 USD (10% discount)
 * boolean isExpensive = price.isGreaterThan(Money.of(100, "USD"));
 * }</pre>
 */
@Value
public class Money {

    BigDecimal amount;
    Currency currency;

    private Money(BigDecimal amount, Currency currency) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }
        this.amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_UP);
        this.currency = currency;
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    public static Money of(double amount, String currencyCode) {
        return new Money(BigDecimal.valueOf(amount), Currency.getInstance(currencyCode));
    }

    public static Money of(long amount, String currencyCode) {
        return new Money(BigDecimal.valueOf(amount), Currency.getInstance(currencyCode));
    }

    public static Money zero(String currencyCode) {
        return new Money(BigDecimal.ZERO, Currency.getInstance(currencyCode));
    }

    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money multiply(double multiplier) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(multiplier)), this.currency);
    }

    public Money multiply(BigDecimal multiplier) {
        return new Money(this.amount.multiply(multiplier), this.currency);
    }

    public Money divide(double divisor) {
        return new Money(
            this.amount.divide(BigDecimal.valueOf(divisor), currency.getDefaultFractionDigits(), RoundingMode.HALF_UP),
            this.currency
        );
    }

    public boolean isGreaterThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isLessThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }

    public boolean isZero() {
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isPositive() {
        return this.amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isNegative() {
        return this.amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public Money negate() {
        return new Money(this.amount.negate(), this.currency);
    }

    public Money abs() {
        return new Money(this.amount.abs(), this.currency);
    }

    private void assertSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                String.format("Cannot operate on different currencies: %s and %s",
                    this.currency.getCurrencyCode(),
                    other.currency.getCurrencyCode())
            );
        }
    }

    @Override
    public String toString() {
        return String.format("%s %s", amount.toPlainString(), currency.getCurrencyCode());
    }
}

