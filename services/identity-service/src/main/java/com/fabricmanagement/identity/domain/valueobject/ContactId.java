package com.fabricmanagement.identity.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a Contact ID.
 */
public final class ContactId {

    private final UUID value;

    private ContactId(UUID value) {
        this.value = Objects.requireNonNull(value, "ContactId value cannot be null");
    }

    public static ContactId of(UUID value) {
        return new ContactId(value);
    }

    public static ContactId of(String value) {
        return new ContactId(UUID.fromString(value));
    }

    public static ContactId generate() {
        return new ContactId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContactId contactId = (ContactId) o;
        return value.equals(contactId.value);
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