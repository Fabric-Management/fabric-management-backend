package com.fabricmanagement.user.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public final class UserId {

    private final UUID value;

    public UserId(UUID value) {
        this.value = Objects.requireNonNull(value, "UserId value must not be null");
    }

    public static UserId fromString(String id) {
        return new UserId(UUID.fromString(id));
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserId userId = (UserId) o;
        return value.equals(userId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

