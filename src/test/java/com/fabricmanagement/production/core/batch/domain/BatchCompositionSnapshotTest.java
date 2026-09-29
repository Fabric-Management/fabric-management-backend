package com.fabricmanagement.production.core.batch.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Strict reading of the batch composition snapshot (FIBER-CATALOG-1 §8). */
class BatchCompositionSnapshotTest {

  private static final UUID COTTON = UUID.randomUUID();
  private static final UUID POLYESTER = UUID.randomUUID();

  @Test
  void roundTripsAnExactComposition() {
    Map<UUID, BigDecimal> composition = new LinkedHashMap<>();
    composition.put(COTTON, new BigDecimal("62.5"));
    composition.put(POLYESTER, new BigDecimal("37.5"));
    Map<String, Object> attributes = new HashMap<>();
    attributes.put(
        BatchCompositionSnapshot.ATTRIBUTE_KEY, BatchCompositionSnapshot.toAttribute(composition));

    assertThat(BatchCompositionSnapshot.isRecorded(attributes)).isTrue();
    assertThat(BatchCompositionSnapshot.read(attributes)).contains(composition);
  }

  @Test
  void readsJsonDeserialisedNumbersAndStringKeys() {
    // JSONB round trip: keys come back as strings, numbers as Double/Integer.
    Map<String, Object> stored = new LinkedHashMap<>();
    stored.put(COTTON.toString(), 62.5);
    stored.put(POLYESTER.toString(), 37.5);

    Map<UUID, BigDecimal> read =
        BatchCompositionSnapshot.read(Map.of(BatchCompositionSnapshot.ATTRIBUTE_KEY, stored))
            .orElseThrow();

    assertThat(read.get(COTTON)).isEqualByComparingTo("62.5");
    assertThat(read.get(POLYESTER)).isEqualByComparingTo("37.5");
  }

  @Test
  void missingEmptyOrMalformedSnapshotsAreUnknownNeverPure() {
    assertThat(BatchCompositionSnapshot.read(null)).isEmpty();
    assertThat(BatchCompositionSnapshot.read(Map.of())).isEmpty();
    assertThat(
            BatchCompositionSnapshot.read(Map.of(BatchCompositionSnapshot.ATTRIBUTE_KEY, Map.of())))
        .isEmpty();
    assertThat(
            BatchCompositionSnapshot.read(
                Map.of(BatchCompositionSnapshot.ATTRIBUTE_KEY, Map.of("not-a-uuid", 60))))
        .isEmpty();
    assertThat(
            BatchCompositionSnapshot.read(
                Map.of(BatchCompositionSnapshot.ATTRIBUTE_KEY, Map.of(COTTON.toString(), "abc"))))
        .isEmpty();
    assertThat(
            BatchCompositionSnapshot.read(
                Map.of(BatchCompositionSnapshot.ATTRIBUTE_KEY, "60% CO / 40% PES")))
        .isEmpty();
  }

  // ── Physical blends (review finding 1) ──────────────────────────────────────────────────────

  private static BatchCompositionSnapshot.InputShare input(
      Map<UUID, BigDecimal> composition, String share) {
    return new BatchCompositionSnapshot.InputShare(composition, new BigDecimal(share));
  }

  @Test
  void mixesInputsWeightedByTheirSharesWithoutRounding() {
    assertThat(
            BatchCompositionSnapshot.mix(
                java.util.List.of(
                    input(Map.of(COTTON, new BigDecimal("100")), "33.3333"),
                    input(
                        Map.of(COTTON, new BigDecimal("62.5"), POLYESTER, new BigDecimal("37.5")),
                        "66.6667"))))
        .contains(
            Map.of(COTTON, new BigDecimal("74.9999875"), POLYESTER, new BigDecimal("25.0000125")));
  }

  @Test
  void anUnknownOrIncompleteInputMakesTheMixUnknown() {
    Map<UUID, BigDecimal> cotton = Map.of(COTTON, new BigDecimal("100"));
    assertThat(
            BatchCompositionSnapshot.mix(java.util.List.of(input(cotton, "50"), input(null, "50"))))
        .isEmpty();
    assertThat(
            BatchCompositionSnapshot.mix(
                java.util.List.of(
                    input(cotton, "50"), input(Map.of(POLYESTER, new BigDecimal("90")), "50"))))
        .isEmpty();
    assertThat(
            BatchCompositionSnapshot.mix(
                java.util.List.of(input(cotton, "50"), input(cotton, "40"))))
        .isEmpty();
    assertThat(BatchCompositionSnapshot.mix(java.util.List.of())).isEmpty();
  }
}
