package com.fabricmanagement.product.fiber.app;

import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.FiberComposition;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.dto.FiberCompositionComponentDto;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pure presentation of a composition (FIBER-CATALOG-1).
 *
 * <p>Components are ordered by percentage descending, shared ISO code ascending, source key
 * ascending ({@code RECYCLED < UNDECLARED < VIRGIN}) and fibre id ascending. The label uses the
 * shared ISO strings with {@code .} decimals and insignificant zeroes stripped; it never rounds,
 * never infers a code from a name and never collapses two source variants of the same ISO code.
 */
public final class FiberCompositionPresenter {

  static final String UNDECLARED_SOURCE_KEY = "UNDECLARED";

  private static final Comparator<FiberCompositionComponentDto> ORDER =
      Comparator.comparing(FiberCompositionComponentDto::percentage, Comparator.reverseOrder())
          .thenComparing(FiberCompositionComponentDto::isoCode)
          .thenComparing(component -> sourceKey(component.materialSource()))
          .thenComparing(component -> component.fiberId().toString());

  private FiberCompositionPresenter() {}

  /**
   * Resolves the complete, ordered component list.
   *
   * @param fiber the fibre being presented
   * @param componentsById every component fibre of a blend, keyed by id (ignored for a pure fibre)
   * @throws IllegalStateException when a stored component cannot be resolved; a partial label would
   *     misstate the composition
   */
  public static List<FiberCompositionComponentDto> components(
      Fiber fiber, Map<UUID, Fiber> componentsById) {
    if (fiber.isPure()) {
      return List.of(component(fiber, FiberComposition.HUNDRED));
    }
    return fiber.getComposition().entrySet().stream()
        .map(
            entry -> {
              Fiber component = componentsById.get(entry.getKey());
              if (component == null) {
                throw new IllegalStateException(
                    "Blend "
                        + fiber.getId()
                        + " references unresolved component "
                        + entry.getKey());
              }
              return component(component, entry.getValue());
            })
        .sorted(ORDER)
        .toList();
  }

  /** Orders already-built components (used for unsaved batch overrides). */
  public static List<FiberCompositionComponentDto> ordered(
      List<FiberCompositionComponentDto> components) {
    return components.stream().sorted(ORDER).toList();
  }

  /** Label such as {@code 60% CO / 40% PES} or {@code 50% PES (recycled) / 50% PES (virgin)}. */
  public static String label(List<FiberCompositionComponentDto> components) {
    boolean sourceRelevant = components.stream().anyMatch(c -> c.materialSource() != null);
    return components.stream()
        .map(component -> labelPart(component, sourceRelevant))
        .collect(Collectors.joining(" / "));
  }

  private static String labelPart(FiberCompositionComponentDto component, boolean withSource) {
    String text = percent(component.percentage()) + "% " + component.isoCode();
    return withSource ? text + " (" + sourceText(component.materialSource()) + ")" : text;
  }

  /** Plain decimal with insignificant zeroes stripped: 60.00 -> 60, 62.50 -> 62.5. */
  public static String percent(BigDecimal percentage) {
    return FiberComposition.strip(percentage).toPlainString();
  }

  private static FiberCompositionComponentDto component(Fiber fiber, BigDecimal percentage) {
    return new FiberCompositionComponentDto(
        fiber.getId(),
        fiber.getFiberIsoCodeId(),
        fiber.getFiberIsoCode() != null ? fiber.getFiberIsoCode().getIsoCode() : "",
        fiber.getFiberName(),
        FiberComposition.strip(percentage),
        fiber.getMaterialSource());
  }

  static String sourceKey(MaterialSource source) {
    return source == null ? UNDECLARED_SOURCE_KEY : source.name();
  }

  private static String sourceText(MaterialSource source) {
    return sourceKey(source).toLowerCase(Locale.ROOT);
  }
}
