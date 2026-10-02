package com.fabricmanagement.product.fiber.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Composition identity semantics (FIBER-CATALOG-1 §5): numeric, order- and scale-independent. */
class FiberCompositionTest {

  private static final UUID A = UUID.fromString("00000000-0000-4000-8000-00000000000a");
  private static final UUID B = UUID.fromString("00000000-0000-4000-8000-00000000000b");

  private static Map<UUID, BigDecimal> of(UUID first, String p1, UUID second, String p2) {
    Map<UUID, BigDecimal> map = new LinkedHashMap<>();
    map.put(first, new BigDecimal(p1));
    map.put(second, new BigDecimal(p2));
    return map;
  }

  @Test
  void normalisationStripsZeroesSortsByIdAndNeverRounds() {
    Map<UUID, BigDecimal> normalised = FiberComposition.normalize(of(B, "37.50", A, "62.5"));

    assertThat(normalised.keySet()).containsExactly(A, B);
    assertThat(normalised.get(A)).isEqualByComparingTo("62.5");
    assertThat(normalised.get(A).toPlainString()).isEqualTo("62.5");
    assertThat(normalised.get(B).toPlainString()).isEqualTo("37.5");
    assertThat(FiberComposition.normalize(of(A, "33.333", B, "66.667")).get(A).toPlainString())
        .isEqualTo("33.333");
    assertThat(FiberComposition.strip(new BigDecimal("100.00")).toPlainString()).isEqualTo("100");
  }

  @Test
  void identityIsNumericAndOrderIndependent() {
    assertThat(FiberComposition.sameComposition(of(A, "60", B, "40"), of(B, "40.00", A, "60.0")))
        .isTrue();
    assertThat(FiberComposition.sameComposition(of(A, "60", B, "40"), of(A, "62.5", B, "37.5")))
        .isFalse();
    assertThat(
            FiberComposition.sameComposition(FiberComposition.pure(A), Map.of(A, BigDecimal.TEN)))
        .isFalse();
  }

  @Test
  void lockKeyIsStableAcrossOrderAndScale() {
    UUID tenant = UUID.randomUUID();

    assertThat(FiberComposition.lockKey(tenant, of(A, "60.00", B, "40")))
        .isEqualTo(FiberComposition.lockKey(tenant, of(B, "40.0", A, "60")));
  }
}
