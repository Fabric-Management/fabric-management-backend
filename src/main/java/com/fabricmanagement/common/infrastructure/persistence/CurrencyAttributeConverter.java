package com.fabricmanagement.common.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Currency;

/**
 * JPA converter that persists {@link java.util.Currency} as its ISO 4217 currency code (3-letter
 * String).
 *
 * <p>{@code autoApply = true} ensures every {@code Currency} field (including those inside
 * {@code @Embeddable} classes like {@link com.fabricmanagement.common.util.Money}) is automatically
 * converted without explicit {@code @Convert} annotations.
 */
@Converter(autoApply = true)
public class CurrencyAttributeConverter implements AttributeConverter<Currency, String> {

  @Override
  public String convertToDatabaseColumn(Currency currency) {
    return currency != null ? currency.getCurrencyCode() : null;
  }

  @Override
  public Currency convertToEntityAttribute(String currencyCode) {
    return currencyCode != null ? Currency.getInstance(currencyCode) : null;
  }
}
