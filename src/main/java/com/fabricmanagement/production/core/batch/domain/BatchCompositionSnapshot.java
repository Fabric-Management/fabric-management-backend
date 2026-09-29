package com.fabricmanagement.production.core.batch.domain;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Effective composition snapshot of a FIBER batch, stored in {@code attributes.composition} as
 * {@code {"<Fiber.id>": percentage}} (FIBER-CATALOG-1).
 *
 * <p>The snapshot is written once when the batch is created, so a later catalogue blend edit never
 * changes the composition QC evaluates for an existing batch. Reading is strict: a missing or
 * malformed snapshot is unknown, never an empty (pure) composition.
 */
public final class BatchCompositionSnapshot {

  public static final String ATTRIBUTE_KEY = "composition";

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  /** One physical input of a blend: its recorded composition and its share of the output (%). */
  public record InputShare(Map<UUID, BigDecimal> composition, BigDecimal percentage) {}

  private BatchCompositionSnapshot() {}

  /** Attribute value for a validated, normalised composition. */
  public static Map<String, Object> toAttribute(Map<UUID, BigDecimal> composition) {
    Map<String, Object> value = new LinkedHashMap<>();
    composition.forEach((fiberId, percentage) -> value.put(fiberId.toString(), percentage));
    return value;
  }

  public static boolean isRecorded(Map<String, Object> attributes) {
    return attributes != null && attributes.containsKey(ATTRIBUTE_KEY);
  }

  /**
   * The stored snapshot, or empty when it is absent or any entry is malformed. Callers treat an
   * empty result as unknown composition.
   */
  public static Optional<Map<UUID, BigDecimal>> read(Map<String, Object> attributes) {
    if (!isRecorded(attributes) || !(attributes.get(ATTRIBUTE_KEY) instanceof Map<?, ?> raw)) {
      return Optional.empty();
    }
    if (raw.isEmpty()) {
      return Optional.empty();
    }
    Map<UUID, BigDecimal> result = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : raw.entrySet()) {
      try {
        UUID key =
            entry.getKey() instanceof UUID uuid
                ? uuid
                : UUID.fromString(String.valueOf(entry.getKey()));
        Object value = entry.getValue();
        if (value == null) {
          return Optional.empty();
        }
        BigDecimal percentage =
            value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
        result.put(key, percentage);
      } catch (IllegalArgumentException malformed) {
        return Optional.empty();
      }
    }
    return Optional.of(result);
  }

  /**
   * Composition of a physical blend, computed from the inputs' recorded compositions weighted by
   * each input's consumption share: {@code sum(share * component / 100)}, exact, no rounding. The
   * result is a recorded fact, so the catalogue's blend rules (component count, minimum share) do
   * not apply.
   *
   * <p>Empty when any input's composition is unknown or malformed, or when the shares or an input
   * do not total exactly 100%. The output is then unknown; it is never replaced by the output
   * product's catalogue definition.
   */
  public static Optional<Map<UUID, BigDecimal>> mix(List<InputShare> inputs) {
    if (inputs == null || inputs.isEmpty()) {
      return Optional.empty();
    }
    BigDecimal shareTotal = BigDecimal.ZERO;
    Map<UUID, BigDecimal> mixed = new LinkedHashMap<>();
    for (InputShare input : inputs) {
      if (input == null
          || input.percentage() == null
          || input.percentage().signum() <= 0
          || input.composition() == null
          || input.composition().isEmpty()) {
        return Optional.empty();
      }
      BigDecimal inputTotal = BigDecimal.ZERO;
      for (Map.Entry<UUID, BigDecimal> component : input.composition().entrySet()) {
        BigDecimal percentage = component.getValue();
        if (component.getKey() == null || percentage == null || percentage.signum() <= 0) {
          return Optional.empty();
        }
        inputTotal = inputTotal.add(percentage);
        mixed.merge(
            component.getKey(),
            percentage.multiply(input.percentage()).movePointLeft(2),
            BigDecimal::add);
      }
      if (inputTotal.compareTo(HUNDRED) != 0) {
        return Optional.empty();
      }
      shareTotal = shareTotal.add(input.percentage());
    }
    if (shareTotal.compareTo(HUNDRED) != 0) {
      return Optional.empty();
    }
    Map<UUID, BigDecimal> result = new LinkedHashMap<>();
    mixed.forEach((fiberId, percentage) -> result.put(fiberId, plain(percentage)));
    return Optional.of(result);
  }

  private static BigDecimal plain(BigDecimal value) {
    BigDecimal stripped = value.stripTrailingZeros();
    return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
  }
}
