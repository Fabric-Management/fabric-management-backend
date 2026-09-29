package com.fabricmanagement.product.fiber.domain;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Composition value semantics: {@code Fiber.id -> percentage}.
 *
 * <p>Identity is ID equality plus BigDecimal numeric equality, independent of map order and scale
 * ({@code 60} equals {@code 60.00}). Normalisation strips insignificant trailing zeroes only; it
 * never rounds a significant decimal.
 */
public final class FiberComposition {

  public static final BigDecimal HUNDRED = new BigDecimal("100");

  private FiberComposition() {}

  /**
   * Returns an ID-ordered copy with insignificant zeroes stripped. Callers validate first; a null
   * key or value is rejected here as a programming error.
   */
  public static Map<UUID, BigDecimal> normalize(Map<UUID, BigDecimal> composition) {
    if (composition == null) {
      return Map.of();
    }
    return composition.entrySet().stream()
        .peek(
            entry -> {
              Objects.requireNonNull(entry.getKey(), "composition key");
              Objects.requireNonNull(entry.getValue(), "composition percentage");
            })
        .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                entry -> strip(entry.getValue()),
                (left, right) -> left,
                LinkedHashMap::new));
  }

  /** Removes insignificant trailing zeroes without changing the numeric value. */
  public static BigDecimal strip(BigDecimal value) {
    BigDecimal stripped = value.stripTrailingZeros();
    return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
  }

  /** Numeric, order-independent equality of two compositions. */
  public static boolean sameComposition(Map<UUID, BigDecimal> left, Map<UUID, BigDecimal> right) {
    Map<UUID, BigDecimal> a = left == null ? Map.of() : left;
    Map<UUID, BigDecimal> b = right == null ? Map.of() : right;
    if (a.size() != b.size()) {
      return false;
    }
    for (Map.Entry<UUID, BigDecimal> entry : a.entrySet()) {
      BigDecimal other = b.get(entry.getKey());
      if (other == null || entry.getValue() == null || entry.getValue().compareTo(other) != 0) {
        return false;
      }
    }
    return true;
  }

  /** Composition of a pure fibre used for effective-composition comparisons. */
  public static Map<UUID, BigDecimal> pure(UUID fiberId) {
    return Map.of(fiberId, HUNDRED);
  }

  /**
   * Stable textual key of a normalised composition (sorted IDs, stripped numbers). Used only for
   * transaction-scoped duplicate locks, never as an identity shown to users.
   */
  public static String lockKey(UUID tenantId, Map<UUID, BigDecimal> composition) {
    return tenantId
        + "|"
        + normalize(composition).entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue().toPlainString())
            .collect(Collectors.joining(","));
  }
}
