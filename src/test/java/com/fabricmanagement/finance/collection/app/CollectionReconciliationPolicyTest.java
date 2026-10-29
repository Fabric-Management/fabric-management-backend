package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CollectionReconciliationPolicyTest {
  static final long HOUR = 3_600_000, SIX_HOURS = 21_600_000;
  final Instant now = Instant.parse("2026-10-23T08:00:00Z");

  @Test
  void tiersAreMeasuredBackFromTheGivenInstant() {
    var policy = new CollectionReconciliationPolicy(500, HOUR, SIX_HOURS, 30);
    var tiers = policy.tiers(now);
    assertThat(tiers.hotSince()).isEqualTo(now.minus(Duration.ofDays(30)));
    assertThat(tiers.hotReadBefore()).isEqualTo(now.minus(Duration.ofHours(1)));
    assertThat(tiers.coldReadBefore()).isEqualTo(now.minus(Duration.ofHours(6)));
    assertThat(policy.readBudget()).isEqualTo(500);
  }

  @Test
  void aColdIntervalShorterThanTheHotOneIsRaisedToIt() {
    var tiers = new CollectionReconciliationPolicy(500, SIX_HOURS, HOUR, 30).tiers(now);
    assertThat(tiers.coldReadBefore()).isEqualTo(tiers.hotReadBefore());
  }

  @Test
  void aZeroOrNegativeIntervalMakesEveryOrderDueOnEveryRun() {
    var tiers = new CollectionReconciliationPolicy(500, 0, -5, 30).tiers(now);
    assertThat(tiers.hotReadBefore()).isEqualTo(now);
    assertThat(tiers.coldReadBefore()).isEqualTo(now);
  }

  @Test
  void aRunAlwaysMayReadAtLeastOneOrder() {
    assertThat(new CollectionReconciliationPolicy(0, HOUR, SIX_HOURS, 30).readBudget())
        .isEqualTo(1);
    assertThat(new CollectionReconciliationPolicy(-9, HOUR, SIX_HOURS, 30).readBudget())
        .isEqualTo(1);
  }

  @Test
  void zeroOrNegativeDaysMakeEveryOrderCold() {
    assertThat(new CollectionReconciliationPolicy(500, HOUR, SIX_HOURS, 0).tiers(now).hotSince())
        .isEqualTo(now);
    assertThat(new CollectionReconciliationPolicy(500, HOUR, SIX_HOURS, -3).tiers(now).hotSince())
        .isEqualTo(now);
  }
}
