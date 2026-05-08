package com.fabricmanagement.production.execution.batch.domain.attributes;

/**
 * Shared utility methods for safely converting raw JSONB attribute values ({@code Object}) into
 * strongly typed Java objects.
 *
 * <p>Used by material-specific attribute records ({@link FiberAttributes}, {@link YarnAttributes},
 * etc.) when deserializing from {@code Batch.attributes} map.
 *
 * <p>All methods are null-safe: {@code null} input yields {@code null} output. Unparseable strings
 * yield {@code null} rather than throwing, so callers can treat missing/corrupt data uniformly.
 */
public final class AttributeConversions {

  private AttributeConversions() {
    // Utility class — prevent instantiation
  }

  /**
   * Convert a raw attribute value to {@link Double}.
   *
   * <p>Handles {@link Number} (Integer, Double, BigDecimal, etc.) and numeric {@link String}
   * values. Returns {@code null} for {@code null} input or unparseable strings.
   */
  public static Double toDouble(Object o) {
    if (o == null) {
      return null;
    }
    if (o instanceof Number n) {
      return n.doubleValue();
    }
    if (o instanceof String s) {
      try {
        return Double.parseDouble(s);
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  /**
   * Convert a raw attribute value to {@link Integer}.
   *
   * <p>Handles {@link Number} and numeric {@link String} values. Returns {@code null} for {@code
   * null} input or unparseable strings.
   */
  public static Integer toInteger(Object o) {
    if (o == null) {
      return null;
    }
    if (o instanceof Number n) {
      return n.intValue();
    }
    if (o instanceof String s) {
      try {
        return Integer.parseInt(s);
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  /**
   * Convert a raw attribute value to {@link String}.
   *
   * <p>Returns {@code null} for {@code null} input; otherwise delegates to {@link
   * Object#toString()}.
   */
  public static String asString(Object o) {
    return o == null ? null : o.toString();
  }
}
