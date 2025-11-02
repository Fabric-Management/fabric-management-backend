package com.fabricmanagement.production.masterdata.fiber.app;

/**
 * Fiber Module Constants.
 *
 * <p>Centralizes all configurable values for fiber business rules.</p>
 * <p><b>CRITICAL:</b> No hardcoded values in services - use these constants.</p>
 */
public final class FiberConstants {

    private FiberConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // =====================================================
    // COMPOSITION VALIDATION RULES
    // =====================================================

    /**
     * Minimum percentage for a fiber component in a blend.
     * <p>Prevents trace amounts that have no practical effect.</p>
     */
    public static final double MIN_COMPONENT_PERCENTAGE = 5.0;

    /**
     * Maximum number of components allowed in a blended fiber.
     * <p>Prevents overly complex blends that are hard to manage.</p>
     */
    public static final int MAX_BLEND_COMPONENTS = 5;

    /**
     * Tolerance for percentage sum validation (100%).
     * <p>Allows floating-point precision differences.</p>
     */
    public static final double PERCENTAGE_TOLERANCE = 0.01;

    /**
     * Expected total percentage for composition.
     */
    public static final double TOTAL_PERCENTAGE = 100.0;

    // =====================================================
    // FIBER NAME VALIDATION
    // =====================================================

    /**
     * Minimum length for fiber name.
     */
    public static final int MIN_FIBER_NAME_LENGTH = 3;

    /**
     * Maximum length for fiber name.
     */
    public static final int MAX_FIBER_NAME_LENGTH = 255;

    // =====================================================
    // COMPOSITION COMPARISON
    // =====================================================

    /**
     * Tolerance for composition percentage comparison.
     * <p>Used when checking for duplicate compositions.</p>
     */
    public static final double COMPOSITION_COMPARISON_TOLERANCE = 0.01;
}

