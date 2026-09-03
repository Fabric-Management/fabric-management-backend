package com.fabricmanagement.common.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AIQueryNormalizerTest {

  private final AIQueryNormalizer normalizer = new AIQueryNormalizer();

  @Test
  void yarnNormalizationComposesYarnAndFiberDictionariesWithoutTouchingDesignation() {
    assertThat(normalizer.normalizeYarnQuery("Penye pamuk iplik Ne 30/2"))
        .isEqualTo("combed cotton yarn ne 30/2");
  }

  @Test
  void yarnSpecificPhraseWinsBeforeFiberSubstringReplacement() {
    assertThat(normalizer.normalizeYarnQuery("elastanlı hava jetli iplik"))
        .isEqualTo("core spun air jet yarn");
  }

  @Test
  void nullAndBlankQueriesRemainUntouched() {
    assertThat(normalizer.normalizeYarnQuery(null)).isNull();
    assertThat(normalizer.normalizeYarnQuery("  ")).isEqualTo("  ");
  }
}
