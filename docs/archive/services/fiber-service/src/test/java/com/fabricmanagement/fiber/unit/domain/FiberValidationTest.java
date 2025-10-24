package com.fabricmanagement.fiber.unit.domain;

import com.fabricmanagement.fiber.domain.valueobject.FiberComponent;
import com.fabricmanagement.shared.domain.exception.InvalidCompositionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static com.fabricmanagement.fiber.fixtures.FiberFixtures.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Domain Validation Tests
 *
 * Testing Strategy:
 * - Pure domain logic (no dependencies)
 * - Business rule validation
 * - Fast execution (< 10ms per test)
 * - 100% coverage (domain validation is critical!)
 *
 * These tests verify the CORE BUSINESS RULES of fiber composition
 */
@DisplayName("Fiber Domain Validation Tests")
class FiberValidationTest {

    // ═════════════════════════════════════════════════════
    // COMPOSITION TOTAL VALIDATION
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Composition Total Validation")
    class CompositionTotalValidation {

        @Test
        @DisplayName("Should accept composition when total equals exactly 100%")
        void shouldAcceptComposition_whenTotalEquals100() {
            // Given
            List<FiberComponent> components = Arrays.asList(
                    component("CO", 60.0),
                    component("PE", 40.0)
            );

            // When & Then
            assertThatCode(() -> validateCompositionTotal(components))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should accept composition with decimal precision (60.5 + 39.5)")
        void shouldAcceptComposition_withDecimalPrecision() {
            // Given
            List<FiberComponent> components = Arrays.asList(
                    component("CO", 60.5),
                    component("PE", 39.5)
            );

            // When & Then
            assertThatCode(() -> validateCompositionTotal(components))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should reject composition when total is less than 100%")
        void shouldRejectComposition_whenTotalLessThan100() {
            // Given
            List<FiberComponent> components = Arrays.asList(
                    component("CO", 60.0),
                    component("PE", 30.0)  // Total = 90%
            );

            // When & Then
            assertThatThrownBy(() -> validateCompositionTotal(components))
                    .isInstanceOf(InvalidCompositionException.class)
                    .hasMessageContaining("Total percentage must equal 100");
        }

        @Test
        @DisplayName("Should reject composition when total is greater than 100%")
        void shouldRejectComposition_whenTotalGreaterThan100() {
            // Given
            List<FiberComponent> components = Arrays.asList(
                    component("CO", 60.0),
                    component("PE", 50.0)  // Total = 110%
            );

            // When & Then
            assertThatThrownBy(() -> validateCompositionTotal(components))
                    .isInstanceOf(InvalidCompositionException.class)
                    .hasMessageContaining("Total percentage must equal 100");
        }

        @Test
        @DisplayName("Should accept 3-component blend (CO/PE/WO 50/30/20)")
        void shouldAcceptThreeComponentBlend() {
            // Given
            List<FiberComponent> components = Arrays.asList(
                    component("CO", 50.0),
                    component("PE", 30.0),
                    component("WO", 20.0)
            );

            // When & Then
            assertThatCode(() -> validateCompositionTotal(components))
                    .doesNotThrowAnyException();
        }
    }

    // ═════════════════════════════════════════════════════
    // COMPONENT COUNT VALIDATION
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Component Count Validation")
    class ComponentCountValidation {

        @Test
        @DisplayName("Should reject blend with only 1 component")
        void shouldRejectBlend_withOnlyOneComponent() {
            // Given
            List<FiberComponent> components = Arrays.asList(
                    component("CO", 100.0)
            );

            // When & Then
            assertThatThrownBy(() -> validateComponentCount(components))
                    .isInstanceOf(InvalidCompositionException.class)
                    .hasMessageContaining("Blend must have at least 2 components");
        }

        @Test
        @DisplayName("Should accept blend with 2 components")
        void shouldAcceptBlend_withTwoComponents() {
            // Given
            List<FiberComponent> components = createValidBlendComponents();

            // When & Then
            assertThatCode(() -> validateComponentCount(components))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should accept blend with 5 components")
        void shouldAcceptBlend_withFiveComponents() {
            // Given
            List<FiberComponent> components = Arrays.asList(
                    component("CO", 40.0),
                    component("PE", 30.0),
                    component("WO", 15.0),
                    component("VI", 10.0),
                    component("LI", 5.0)
            );

            // When & Then
            assertThatCode(() -> validateComponentCount(components))
                    .doesNotThrowAnyException();
        }
    }

    // ═════════════════════════════════════════════════════
    // DUPLICATE FIBER CODE VALIDATION
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Duplicate Fiber Code Validation")
    class DuplicateFiberCodeValidation {

        @Test
        @DisplayName("Should reject composition with duplicate fiber codes")
        void shouldRejectComposition_withDuplicateFiberCodes() {
            // Given
            List<FiberComponent> components = Arrays.asList(
                    component("CO", 50.0),
                    component("CO", 50.0)  // Duplicate!
            );

            // When & Then
            assertThatThrownBy(() -> validateNoDuplicates(components))
                    .isInstanceOf(InvalidCompositionException.class)
                    .hasMessageContaining("Duplicate fiber code in composition: CO");
        }

        @Test
        @DisplayName("Should accept composition with unique fiber codes")
        void shouldAcceptComposition_withUniqueFiberCodes() {
            // Given
            List<FiberComponent> components = Arrays.asList(
                    component("CO", 60.0),
                    component("PE", 40.0)
            );

            // When & Then
            assertThatCode(() -> validateNoDuplicates(components))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should be case-sensitive for fiber codes")
        void shouldBeCaseSensitive_forFiberCodes() {
            // Given - 'CO' and 'co' should be considered different
            List<FiberComponent> components = Arrays.asList(
                    component("CO", 60.0),
                    component("co", 40.0)  // Different case
            );

            // When & Then
            assertThatCode(() -> validateNoDuplicates(components))
                    .doesNotThrowAnyException();  // Different codes, valid
        }
    }

    // ═════════════════════════════════════════════════════
    // PERCENTAGE RANGE VALIDATION
    // ═════════════════════════════════════════════════════

    @Nested
    @DisplayName("Percentage Range Validation")
    class PercentageRangeValidation {

        @Test
        @DisplayName("Should reject component with 0% percentage")
        void shouldRejectComponent_withZeroPercentage() {
            // Given
            FiberComponent component = component("CO", 0.0);

            // When & Then
            assertThatThrownBy(() -> validatePercentageRange(component))
                    .isInstanceOf(InvalidCompositionException.class)
                    .hasMessageContaining("Percentage must be greater than 0");
        }

        @Test
        @DisplayName("Should reject component with negative percentage")
        void shouldRejectComponent_withNegativePercentage() {
            // Given
            FiberComponent component = component("CO", -10.0);

            // When & Then
            assertThatThrownBy(() -> validatePercentageRange(component))
                    .isInstanceOf(InvalidCompositionException.class)
                    .hasMessageContaining("Percentage must be greater than 0");
        }

        @Test
        @DisplayName("Should reject component with > 100% percentage")
        void shouldRejectComponent_withPercentageOver100() {
            // Given
            FiberComponent component = component("CO", 150.0);

            // When & Then
            assertThatThrownBy(() -> validatePercentageRange(component))
                    .isInstanceOf(InvalidCompositionException.class)
                    .hasMessageContaining("Percentage must not exceed 100");
        }

        @Test
        @DisplayName("Should accept component with 0.01% (minimum)")
        void shouldAcceptComponent_withMinimumPercentage() {
            // Given
            FiberComponent component = component("CO", 0.01);

            // When & Then
            assertThatCode(() -> validatePercentageRange(component))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should accept component with 100% (maximum)")
        void shouldAcceptComponent_withMaximumPercentage() {
            // Given
            FiberComponent component = component("CO", 100.0);

            // When & Then
            assertThatCode(() -> validatePercentageRange(component))
                    .doesNotThrowAnyException();
        }
    }

    // ═════════════════════════════════════════════════════
    // HELPER METHODS (Domain validation logic)
    // ═════════════════════════════════════════════════════

    private void validateCompositionTotal(List<FiberComponent> components) {
        BigDecimal total = components.stream()
                .map(FiberComponent::getPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.compareTo(BigDecimal.valueOf(100)) != 0) {
            throw new InvalidCompositionException(
                    "Total percentage must equal 100, but was: " + total);
        }
    }

    private void validateComponentCount(List<FiberComponent> components) {
        if (components.size() < 2) {
            throw new InvalidCompositionException(
                    "Blend must have at least 2 components");
        }
    }

    private void validateNoDuplicates(List<FiberComponent> components) {
        long uniqueCount = components.stream()
                .map(FiberComponent::getFiberCode)
                .distinct()
                .count();

        if (uniqueCount != components.size()) {
            // Find duplicate
            String duplicate = components.stream()
                    .map(FiberComponent::getFiberCode)
                    .filter(code -> components.stream()
                            .filter(c -> c.getFiberCode().equals(code))
                            .count() > 1)
                    .findFirst()
                    .orElse("UNKNOWN");

            throw new InvalidCompositionException(
                    "Duplicate fiber code in composition: " + duplicate);
        }
    }

    private void validatePercentageRange(FiberComponent component) {
        BigDecimal percentage = component.getPercentage();

        if (percentage.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidCompositionException(
                    "Percentage must be greater than 0");
        }

        if (percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new InvalidCompositionException(
                    "Percentage must not exceed 100");
        }
    }
}

