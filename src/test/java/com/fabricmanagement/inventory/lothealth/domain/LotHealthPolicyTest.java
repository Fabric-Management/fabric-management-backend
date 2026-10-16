package com.fabricmanagement.inventory.lothealth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Lot-health rules (Fatih, 2026-10-14): closed, or at least the minimum of normal pieces. */
class LotHealthPolicyTest {

  private static final BigDecimal AVERAGE = new BigDecimal("100");
  private final LotHealthPolicy standard = LotHealthPolicy.STANDARD;

  @Test
  void byCount_oneOrTwoPiecesLeftAreARemnant_noneOrThreeAreNot() {
    assertThat(standard.leavesRemnant(0)).isFalse();
    assertThat(standard.leavesRemnant(1)).isTrue();
    assertThat(standard.leavesRemnant(2)).isTrue();
    assertThat(standard.leavesRemnant(3)).isFalse();
  }

  @Test
  void aPieceUnder51PercentOfTheAverageIsShort_51IsNot() {
    assertThat(standard.isShort(new BigDecimal("50.99"), AVERAGE)).isTrue();
    assertThat(standard.isShort(new BigDecimal("51"), AVERAGE)).isFalse();
    assertThat(standard.isShort(new BigDecimal("100"), AVERAGE)).isFalse();
  }

  @Test
  void fortyThirtyFiftyFive_isOneNormalPiece_aRemnant() {
    List<BigDecimal> left = metres(40, 30, 55);

    assertThat(standard.normalPieces(left, AVERAGE)).isEqualTo(1);
    assertThat(standard.healthyRemainder(left, AVERAGE)).isFalse();
  }

  @Test
  void threeFullPiecesAndAShortOne_isHealthy() {
    assertThat(standard.healthyRemainder(metres(100, 100, 100, 30), AVERAGE)).isTrue();
    assertThat(standard.healthyRemainder(metres(100, 120, 120), AVERAGE)).isTrue();
  }

  @Test
  void twoFullPiecesAndAShortOne_isARemnant() {
    assertThat(standard.healthyRemainder(metres(100, 100, 20), AVERAGE)).isFalse();
  }

  @Test
  void aClosedLot_keepsNothing_andIsHealthy() {
    assertThat(standard.healthyRemainder(List.of(), AVERAGE)).isTrue();
  }

  @Test
  void theAverageIsTotalOverCount_andUnknownWithoutPieces() {
    assertThat(LotHealthPolicy.averagePiece(metres(100, 120, 80))).isEqualByComparingTo("100");
    assertThat(LotHealthPolicy.averagePiece(metres(95, 95, 100)))
        .isEqualByComparingTo(new BigDecimal("96.66666666666667"));
    assertThat(LotHealthPolicy.averagePiece(List.of())).isNull();
  }

  @Test
  void withoutAPositiveAverage_noPieceIsShort() {
    assertThat(standard.isShort(BigDecimal.ONE, BigDecimal.ZERO)).isFalse();
  }

  @Test
  void aSlowerProductsHigherMinimumAppliesToNormalPiecesToo() {
    LotHealthPolicy slowing = new LotHealthPolicy(5, new BigDecimal("0.51"));

    assertThat(slowing.leavesRemnant(4)).isTrue();
    assertThat(slowing.healthyRemainder(metres(100, 100, 100, 100, 30), AVERAGE)).isFalse();
    assertThat(slowing.healthyRemainder(metres(100, 100, 100, 100, 100), AVERAGE)).isTrue();
  }

  @Test
  void invalidThresholdsAreRefused() {
    assertThatThrownBy(() -> new LotHealthPolicy(0, new BigDecimal("0.51")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LotHealthPolicy(3, BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LotHealthPolicy(3, BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static List<BigDecimal> metres(int... values) {
    return Arrays.stream(values).mapToObj(BigDecimal::valueOf).toList();
  }
}
