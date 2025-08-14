package com.fabricmanagement.user.domain.valueobject;

import com.fabricmanagement.common.core.domain.ValueObject;
import lombok.Getter;

import java.util.UUID;

@Getter
public class TenantId extends ValueObject {

    private final UUID value;

    private TenantId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("TenantId cannot be null");
        }
        this.value = value;
    }

    public static TenantId of(UUID value) {
        return new TenantId(value);
    }

    public static TenantId of(String value) {
        return new TenantId(UUID.fromString(value));
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