package com.fabricmanagement.finance.collection.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The three times a projection keeps: when it was checked against the source, when it was last read
 * in full, and when the source last differed from it. Reconciliation sorts orders into tiers by
 * them, so each must move for exactly the reason it is named after.
 */
class CollectionProjectionTest {
  final UUID revision = UUID.randomUUID();
  final Instant first = Instant.parse("2026-10-01T10:00:00Z");
  final Instant second = Instant.parse("2026-10-02T10:00:00Z");
  final Instant third = Instant.parse("2026-10-03T10:00:00Z");
  CollectionProjection projection;

  @BeforeEach
  void setup() {
    projection = CollectionProjection.create(UUID.randomUUID(), UUID.randomUUID());
    projection.observed(revision, 3, 7L, false, false, first);
  }

  @Test
  void theFirstReadIsAChangeAndSetsAllThreeTimes() {
    assertThat(projection.getSourceChangedAt()).isEqualTo(first);
    assertThat(projection.getSourceReadAt()).isEqualTo(first);
    assertThat(projection.getCheckedAt()).isEqualTo(first);
  }

  @Test
  void readingTheSameSourceAgainMovesTheReadAndCheckTimesButNotTheChangeTime() {
    projection.observed(revision, 3, 7L, false, false, second);
    assertThat(projection.getSourceReadAt()).isEqualTo(second);
    assertThat(projection.getCheckedAt()).isEqualTo(second);
    assertThat(projection.getSourceChangedAt()).isEqualTo(first);
  }

  @Test
  void anotherRevisionPlanVersionOrderVersionCancellationOrStalenessIsAChange() {
    projection.observed(UUID.randomUUID(), 3, 7L, false, false, second);
    assertThat(projection.getSourceChangedAt()).isEqualTo(second);
    projection.observed(projection.getRevisionId(), 4, 7L, false, false, third);
    assertThat(projection.getSourceChangedAt()).isEqualTo(third);

    var other = CollectionProjection.create(UUID.randomUUID(), UUID.randomUUID());
    other.observed(revision, 3, 7L, false, false, first);
    other.observed(revision, 3, 8L, false, false, second);
    assertThat(other.getSourceChangedAt()).isEqualTo(second);
    other.observed(revision, 3, 8L, true, false, third);
    assertThat(other.getSourceChangedAt()).isEqualTo(third);

    var stale = CollectionProjection.create(UUID.randomUUID(), UUID.randomUUID());
    stale.observed(revision, 3, 7L, false, false, first);
    stale.observed(revision, 3, 7L, false, true, second);
    assertThat(stale.getSourceChangedAt()).isEqualTo(second);
  }

  @Test
  void aSourceThatWasMissingAndIsBackIsAChange() {
    projection.missing(second);
    projection.observed(revision, 3, 7L, false, false, third);
    assertThat(projection.isSourceMissing()).isFalse();
    assertThat(projection.getSourceChangedAt()).isEqualTo(third);
  }

  @Test
  void aSourceFoundMissingIsAChangeOnceAndThenOnlyReadAgain() {
    projection.missing(second);
    assertThat(projection.isSourceMissing()).isTrue();
    assertThat(projection.getSourceChangedAt()).isEqualTo(second);
    assertThat(projection.getSourceReadAt()).isEqualTo(second);
    assertThat(projection.getCheckedAt()).isEqualTo(second);
    projection.missing(third);
    assertThat(projection.getSourceChangedAt()).isEqualTo(second);
    assertThat(projection.getSourceReadAt()).isEqualTo(third);
    assertThat(projection.getCheckedAt()).isEqualTo(third);
  }
}
