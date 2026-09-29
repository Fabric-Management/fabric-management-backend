package com.fabricmanagement.product.fiber.app;

import java.math.BigDecimal;

/**
 * Fiber module constants.
 *
 * <p>The component limits are current application limits kept unchanged by FIBER-CATALOG-1; they
 * are not claimed textile standards and their business review is separate.
 */
public final class FiberConstants {

  private FiberConstants() {
    throw new UnsupportedOperationException("Utility class");
  }

  /** Minimum share of one component in a blend definition (application limit). */
  public static final BigDecimal MIN_COMPONENT_PERCENTAGE = new BigDecimal("5");

  /** Maximum number of components in one composition (application limit). */
  public static final int MAX_BLEND_COMPONENTS = 5;

  /** Minimum number of components in a blend definition. */
  public static final int MIN_BLEND_COMPONENTS = 2;

  /** Minimum length for fiber name. */
  public static final int MIN_FIBER_NAME_LENGTH = 3;

  /** Maximum length for fiber name. */
  public static final int MAX_FIBER_NAME_LENGTH = 255;
}
