package com.fabricmanagement.common.util;

import lombok.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Value object representing measurement units with quantity.
 *
 * <p>Immutable representation of quantities with units (kg, meters, pieces, etc.)
 * Ensures type-safety and prevents unit-related calculation errors.</p>
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>Immutable (thread-safe)</li>
 *   <li>Unit-aware operations</li>
 *   <li>Conversion support</li>
 *   <li>Type-safe arithmetic</li>
 *   <li>Prevents mixing incompatible units</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * Unit weight = Unit.of(150.5, "KG");
 * Unit length = Unit.of(2500, "METER");
 *
 * Unit total = weight.add(Unit.of(50, "KG"));  // 200.5 KG
 * Unit half = weight.divide(2);  // 75.25 KG
 *
 * // Convert meters to kilometers
 * Unit km = length.convertTo("KM", 1000);  // 2.5 KM
 * }</pre>
 */
@Value
public class Unit {

    BigDecimal quantity;
    String unitCode;

    private Unit(BigDecimal quantity, String unitCode) {
        if (quantity == null) {
            throw new IllegalArgumentException("Quantity cannot be null");
        }
        if (unitCode == null || unitCode.isBlank()) {
            throw new IllegalArgumentException("Unit code cannot be null or empty");
        }
        this.quantity = quantity.setScale(3, RoundingMode.HALF_UP);
        this.unitCode = unitCode.toUpperCase();
    }

    public static Unit of(BigDecimal quantity, String unitCode) {
        return new Unit(quantity, unitCode);
    }

    public static Unit of(double quantity, String unitCode) {
        return new Unit(BigDecimal.valueOf(quantity), unitCode);
    }

    public static Unit of(long quantity, String unitCode) {
        return new Unit(BigDecimal.valueOf(quantity), unitCode);
    }

    public static Unit zero(String unitCode) {
        return new Unit(BigDecimal.ZERO, unitCode);
    }

    public Unit add(Unit other) {
        assertSameUnit(other);
        return new Unit(this.quantity.add(other.quantity), this.unitCode);
    }

    public Unit subtract(Unit other) {
        assertSameUnit(other);
        return new Unit(this.quantity.subtract(other.quantity), this.unitCode);
    }

    public Unit multiply(double multiplier) {
        return new Unit(this.quantity.multiply(BigDecimal.valueOf(multiplier)), this.unitCode);
    }

    public Unit multiply(BigDecimal multiplier) {
        return new Unit(this.quantity.multiply(multiplier), this.unitCode);
    }

    public Unit divide(double divisor) {
        return new Unit(
            this.quantity.divide(BigDecimal.valueOf(divisor), 3, RoundingMode.HALF_UP),
            this.unitCode
        );
    }

    public Unit divide(BigDecimal divisor) {
        return new Unit(
            this.quantity.divide(divisor, 3, RoundingMode.HALF_UP),
            this.unitCode
        );
    }

    public Unit convertTo(String targetUnitCode, double conversionFactor) {
        return new Unit(
            this.quantity.divide(BigDecimal.valueOf(conversionFactor), 3, RoundingMode.HALF_UP),
            targetUnitCode
        );
    }

    public boolean isGreaterThan(Unit other) {
        assertSameUnit(other);
        return this.quantity.compareTo(other.quantity) > 0;
    }

    public boolean isLessThan(Unit other) {
        assertSameUnit(other);
        return this.quantity.compareTo(other.quantity) < 0;
    }

    public boolean isZero() {
        return this.quantity.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isPositive() {
        return this.quantity.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isNegative() {
        return this.quantity.compareTo(BigDecimal.ZERO) < 0;
    }

    private void assertSameUnit(Unit other) {
        if (!this.unitCode.equals(other.unitCode)) {
            throw new IllegalArgumentException(
                String.format("Cannot operate on different units: %s and %s",
                    this.unitCode, other.unitCode)
            );
        }
    }

    @Override
    public String toString() {
        return String.format("%s %s", quantity.toPlainString(), unitCode);
    }
}

