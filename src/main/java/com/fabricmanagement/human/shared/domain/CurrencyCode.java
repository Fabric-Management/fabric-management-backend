package com.fabricmanagement.human.shared.domain;

import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

public record CurrencyCode(String value) {

    public CurrencyCode {
        Objects.requireNonNull(value, "Currency code cannot be null");
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new IllegalArgumentException("Currency code must be 3 letters (ISO 4217)");
        }
        try {
            Currency.getInstance(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported currency code: " + normalized, ex);
        }
        value = normalized;
    }

    public static CurrencyCode of(String code) {
        return new CurrencyCode(code);
    }

    @Override
    public String toString() {
        return value;
    }
}

