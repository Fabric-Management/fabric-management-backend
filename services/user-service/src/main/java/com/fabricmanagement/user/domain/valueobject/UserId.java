package com.fabricmanagement.user.domain.valueobject;

import com.fabricmanagement.common.core.domain.ValueObject;
import lombok.Getter;

import java.util.UUID;

@Getter
public class UserId extends ValueObject {

    private final UUID value;

    private UserId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        this.value = value;
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    @Override
    protected Object[] getEqualityComponents() {
        return new Object[] { value };
    }

    @Override
    public String toString() {
        return value.toString();
    }
}