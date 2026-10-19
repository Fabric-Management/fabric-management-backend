package com.fabricmanagement.product.fabric.app;

import com.fabricmanagement.product.fabric.domain.article.FabricArticleSpec;
import com.fabricmanagement.product.fabric.dto.FabricCompositionDto;
import com.fabricmanagement.product.fabric.dto.FabricCompositionSuggestionDto;
import com.fabricmanagement.product.fabric.dto.FabricYarnRequest;
import com.fabricmanagement.product.yarn.api.facade.YarnFacade;
import com.fabricmanagement.product.yarn.dto.YarnCompositionLineDto;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Works out a fabric's fibre composition from its yarns (FABRIC-2): a fibre's share of the fabric
 * is the sum, over the yarns, of the yarn's share of the mass times the fibre's percentage in that
 * yarn. The result is rounded to hundredths so the lines add up to exactly 100; the remainder goes
 * to the lines that lost most in rounding. The tenant may still change it before saving.
 */
@Service
@RequiredArgsConstructor
public class FabricCompositionSuggestionService {

  public static final String NO_YARNS = "NO_YARNS";
  public static final String YARN_SHARES_INCOMPLETE = "YARN_SHARES_INCOMPLETE";
  public static final String YARN_COMPOSITION_MISSING = "YARN_COMPOSITION_MISSING";

  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private static final MathContext EXACT_ENOUGH = new MathContext(20, RoundingMode.HALF_EVEN);

  private final FabricMaterialResolver materials;
  private final YarnFacade yarns;

  @Transactional(readOnly = true)
  public FabricCompositionSuggestionDto suggest(List<FabricYarnRequest> requested) {
    List<FabricArticleSpec.YarnLine> lines = materials.yarnLines(requested);
    if (lines.isEmpty()) {
      return new FabricCompositionSuggestionDto(List.of(), List.of(), List.of(NO_YARNS));
    }
    List<String> problems = new ArrayList<>();
    BigDecimal shares =
        lines.stream()
            .map(FabricArticleSpec.YarnLine::sharePercent)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (shares.compareTo(HUNDRED) != 0) {
      problems.add(YARN_SHARES_INCOMPLETE);
    }

    Map<UUID, List<YarnCompositionLineDto>> declared =
        yarns.findDeclaredCompositions(
            lines.stream().map(FabricArticleSpec.YarnLine::yarnProductId).distinct().toList());
    List<UUID> missing = new ArrayList<>();
    Map<UUID, Mass> masses = new LinkedHashMap<>();
    for (FabricArticleSpec.YarnLine line : lines) {
      List<YarnCompositionLineDto> fibres = declared.get(line.yarnProductId());
      if (fibres == null) {
        if (!missing.contains(line.yarnProductId())) missing.add(line.yarnProductId());
        continue;
      }
      BigDecimal yarnTotal =
          fibres.stream()
              .map(YarnCompositionLineDto::percentage)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      if (yarnTotal.signum() <= 0) {
        if (!missing.contains(line.yarnProductId())) missing.add(line.yarnProductId());
        continue;
      }
      for (YarnCompositionLineDto fibre : fibres) {
        BigDecimal mass =
            line.sharePercent().multiply(fibre.percentage()).divide(yarnTotal, EXACT_ENOUGH);
        masses.computeIfAbsent(fibre.fiberId(), id -> new Mass(fibre)).add(mass);
      }
    }
    if (!missing.isEmpty()) {
      problems.add(YARN_COMPOSITION_MISSING);
    }
    return new FabricCompositionSuggestionDto(
        toHundred(List.copyOf(masses.values())), List.copyOf(missing), List.copyOf(problems));
  }

  /**
   * Scales the masses to 100 and rounds them to hundredths with the largest-remainder method, so
   * the lines add up to exactly 100.00 and no line moves by more than one hundredth.
   */
  static List<FabricCompositionDto> toHundred(List<Mass> masses) {
    BigDecimal total = masses.stream().map(Mass::value).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (total.signum() <= 0) return List.of();
    List<Rounded> rounded = new ArrayList<>();
    long units = 0;
    for (Mass mass : masses) {
      BigDecimal exact =
          mass.value().multiply(BigDecimal.valueOf(10_000)).divide(total, EXACT_ENOUGH);
      BigDecimal floor = exact.setScale(0, RoundingMode.FLOOR);
      rounded.add(new Rounded(mass, floor.longValueExact(), exact.subtract(floor)));
      units += floor.longValueExact();
    }
    List<Rounded> byRemainder =
        rounded.stream()
            .sorted(
                Comparator.comparing(Rounded::remainder)
                    .reversed()
                    .thenComparing(item -> item.mass().isoCode()))
            .toList();
    for (int index = 0; units < 10_000; index++, units++) {
      byRemainder.get(index % byRemainder.size()).bump();
    }
    return rounded.stream()
        .filter(item -> item.units > 0)
        .sorted(
            Comparator.comparingLong((Rounded item) -> item.units)
                .reversed()
                .thenComparing(item -> item.mass().isoCode()))
        .map(
            item ->
                new FabricCompositionDto(
                    item.mass().fiberId(),
                    item.mass().isoCode(),
                    item.mass().fiberName(),
                    item.mass().source(),
                    BigDecimal.valueOf(item.units, 2)))
        .toList();
  }

  /** A fibre's accumulated share of the fabric's mass. */
  static final class Mass {
    private final YarnCompositionLineDto fibre;
    private BigDecimal value = BigDecimal.ZERO;

    Mass(YarnCompositionLineDto fibre) {
      this.fibre = fibre;
    }

    Mass add(BigDecimal amount) {
      value = value.add(amount);
      return this;
    }

    BigDecimal value() {
      return value;
    }

    UUID fiberId() {
      return fibre.fiberId();
    }

    String isoCode() {
      return fibre.isoCode();
    }

    String fiberName() {
      return fibre.fiberName();
    }

    com.fabricmanagement.product.fiber.domain.MaterialSource source() {
      return fibre.materialSource();
    }
  }

  private static final class Rounded {
    private final Mass mass;
    private long units;
    private final BigDecimal remainder;

    Rounded(Mass mass, long units, BigDecimal remainder) {
      this.mass = mass;
      this.units = units;
      this.remainder = remainder;
    }

    Mass mass() {
      return mass;
    }

    BigDecimal remainder() {
      return remainder;
    }

    void bump() {
      units++;
    }
  }
}
