package com.fabricmanagement.common.core.domain;

public abstract class ValueObject {

    protected abstract Object[] getEqualityComponents();

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }

        ValueObject other = (ValueObject) obj;
        Object[] thisComponents = getEqualityComponents();
        Object[] otherComponents = other.getEqualityComponents();

        if (thisComponents.length != otherComponents.length) {
            return false;
        }

        for (int i = 0; i < thisComponents.length; i++) {
            if (thisComponents[i] == null) {
                if (otherComponents[i] != null) {
                    return false;
                }
            } else if (!thisComponents[i].equals(otherComponents[i])) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(getEqualityComponents());
    }
}