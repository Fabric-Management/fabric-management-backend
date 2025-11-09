package com.fabricmanagement.human.shared.domain;

import java.util.Locale;
import java.util.Objects;

public record LeaveTypeCode(String value) {

    private static final int MAX_LENGTH = 50;

    public LeaveTypeCode {
        Objects.requireNonNull(value, "LeaveTypeCode value cannot be null");
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("LeaveTypeCode cannot be blank");
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("LeaveTypeCode must not exceed " + MAX_LENGTH + " characters");
        }
        value = normalized;
    }

    public static LeaveTypeCode of(String value) {
        return new LeaveTypeCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

