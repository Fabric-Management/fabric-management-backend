package com.fabricmanagement.finance.collection.app;

import static com.fabricmanagement.finance.collection.app.CollectionReconciliationScanService.Verdict.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.app.CollectionReconciliationPolicy.Tiers;
import com.fabricmanagement.finance.collection.app.CollectionReconciliationScanService.Finding;
import com.fabricmanagement.finance.collection.app.CollectionReconciliationScanService.Verdict;
import com.fabricmanagement.finance.collection.app.port.CollectionSourcePort.SourceStamp;
import com.fabricmanagement.finance.collection.domain.CollectionProjection;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionProjectionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class CollectionReconciliationScanTest {
  static final UUID FIRST = new UUID(0, 0);
  final UUID tenant = UUID.randomUUID(),
      order = UUID.randomUUID(),
      plan = UUID.randomUUID(),
      revision = UUID.randomUUID();
  final Instant now = Instant.parse("2026-10-23T08:00:00Z");

  /** Hot when it changed within 30 days; read again after 1 hour (hot) or 6 hours (cold). */
  final Tiers tiers =
      new Tiers(
          now.minus(Duration.ofDays(30)),
          now.minus(Duration.ofHours(1)),
          now.minus(Duration.ofHours(6)));

  final Instant recently = now.minus(Duration.ofMinutes(5));
  final Instant active = now.minus(Duration.ofDays(2));
  final Instant dormant = now.minus(Duration.ofDays(60));
  CollectionProjectionRepository projections;
  CollectionReconciliationScanService scan;

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    projections = mock(CollectionProjectionRepository.class);
    scan =
        new CollectionReconciliationScanService(
            projections, mock(CollectionPaymentMatchRepository.class));
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  /** Plan version 3, order version 7, not cancelled; last differed at {@code changedAt}. */
  private CollectionProjection recorded(UUID forOrder, Instant changedAt, Instant readAt) {
    var value = CollectionProjection.create(forOrder, plan);
    value.observed(revision, 3, 7L, false, false, changedAt);
    if (!readAt.equals(changedAt)) value.observed(revision, 3, 7L, false, false, readAt);
    return value;
  }

  private SourceStamp stamp(UUID forOrder, UUID rev, long planVersion, long orderVersion) {
    return new SourceStamp(forOrder, orderVersion, planVersion, rev, false);
  }

  private SourceStamp matching(UUID forOrder) {
    return stamp(forOrder, revision, 3, 7);
  }

  private void inRange(UUID after, UUID upTo, CollectionProjection... rows) {
    when(projections.findByTenantIdAndOrderIdGreaterThanAndOrderIdLessThanEqualOrderByOrderId(
            tenant, after, upTo))
        .thenReturn(List.of(rows));
  }

  private Verdict verdictOf(SourceStamp listed, CollectionProjection... rows) {
    inRange(FIRST, listed.orderId(), rows);
    return scan.triage(FIRST, List.of(listed), tiers).getFirst().verdict();
  }

  @Test
  void anOrderWhoseStampMatchesAndWasReadRecentlyInTheHotTierOnlyNeedsItsCheckTimeMoved() {
    assertThat(verdictOf(matching(order), recorded(order, active, recently)))
        .isEqualTo(CURRENT_HOT);
  }

  @Test
  void aHotOrderNotReadForAnHourIsDue() {
    var wait = recorded(order, active, now.minus(Duration.ofMinutes(61)));
    assertThat(verdictOf(matching(order), wait)).isEqualTo(DUE);
  }

  @Test
  void theHourIsAnInclusiveBoundary() {
    var wait = recorded(order, active, now.minus(Duration.ofHours(1)));
    assertThat(verdictOf(matching(order), wait)).isEqualTo(DUE);
  }

  @Test
  void aColdOrderReadWithinSixHoursNeedsNothingAtAll() {
    var wait = recorded(order, dormant, now.minus(Duration.ofHours(2)));
    assertThat(verdictOf(matching(order), wait)).isEqualTo(CURRENT_COLD);
  }

  @Test
  void aColdOrderIsDueOnlyAfterSixHoursNotAfterOne() {
    var overAnHour = recorded(order, dormant, now.minus(Duration.ofMinutes(61)));
    var overSixHours = recorded(order, dormant, now.minus(Duration.ofHours(7)));
    assertThat(verdictOf(matching(order), overAnHour)).isEqualTo(CURRENT_COLD);
    assertThat(verdictOf(matching(order), overSixHours)).isEqualTo(DUE);
  }

  @Test
  void aProjectionThatChangedThirtyDaysAgoIsStillHotAtTheEdge() {
    var edge = recorded(order, tiers.hotSince(), recently);
    assertThat(verdictOf(matching(order), edge)).isEqualTo(CURRENT_HOT);
  }

  @Test
  void anAgreedOrderFinanceNeverBuiltIsChangedAndHasNeverBeenRead() {
    inRange(FIRST, order);
    var found = scan.triage(FIRST, List.of(matching(order)), tiers);
    assertThat(found).containsExactly(new Finding(order, CHANGED, null));
  }

  @Test
  void anotherPlanVersionAnotherRevisionOrACancellationIsChanged() {
    var row = recorded(order, active, recently);
    assertThat(verdictOf(stamp(order, revision, 4, 7), row)).isEqualTo(CHANGED);
    assertThat(verdictOf(stamp(order, UUID.randomUUID(), 3, 7), row)).isEqualTo(CHANGED);
    assertThat(verdictOf(new SourceStamp(order, 7, 3, revision, true), row)).isEqualTo(CHANGED);
  }

  @Test
  void anEditedOrderIsChangedEvenWhenThePlanVersionDidNotMove() {
    var row = recorded(order, active, recently);
    assertThat(verdictOf(stamp(order, revision, 3, 8), row)).isEqualTo(CHANGED);
  }

  @Test
  void changedWinsOverEveryTierEvenForADormantOrderReadJustNow() {
    var row = recorded(order, dormant, recently);
    assertThat(verdictOf(stamp(order, revision, 3, 8), row)).isEqualTo(CHANGED);
  }

  @Test
  void aProjectionLastSeenWithItsSourceMissingIsChangedOnceTheSourceIsBack() {
    var row = recorded(order, active, recently);
    row.missing(recently);
    assertThat(verdictOf(matching(order), row)).isEqualTo(CHANGED);
  }

  @Test
  void theFindingCarriesWhenTheOrderWasLastReadSoTheLongestWaitingGoFirst() {
    var wait = recorded(order, active, now.minus(Duration.ofHours(3)));
    inRange(FIRST, order, wait);
    var found = scan.triage(FIRST, List.of(matching(order)), tiers);
    assertThat(found).containsExactly(new Finding(order, DUE, now.minus(Duration.ofHours(3))));
  }

  @Test
  void aProjectionInThePageRangeThatIsNotListedAndWasNotMissingIsChanged() {
    UUID listed = UUID.randomUUID(), orphan = UUID.randomUUID();
    inRange(FIRST, listed, recorded(listed, active, recently), recorded(orphan, active, recently));
    var found = scan.triage(FIRST, List.of(matching(listed)), tiers);
    assertThat(found)
        .extracting(Finding::orderId, Finding::verdict)
        .containsExactly(tuple(listed, CURRENT_HOT), tuple(orphan, CHANGED));
  }

  @Test
  void anOrphanAlreadyRecordedAsMissingFollowsItsTierInsteadOfBeingReadEveryRun() {
    UUID listed = UUID.randomUUID(), gone = UUID.randomUUID(), goneLong = UUID.randomUUID();
    var recent = recorded(gone, active, recently);
    recent.missing(recently);
    var forgotten = recorded(goneLong, active, recently);
    forgotten.missing(now.minus(Duration.ofHours(2)));
    inRange(FIRST, listed, recorded(listed, active, recently), recent, forgotten);
    var found = scan.triage(FIRST, List.of(matching(listed)), tiers);
    assertThat(found)
        .filteredOn(finding -> !finding.orderId().equals(listed))
        .extracting(Finding::orderId, Finding::verdict)
        .containsExactlyInAnyOrder(tuple(gone, CURRENT_HOT), tuple(goneLong, DUE));
  }

  @Test
  void theRangeIsTheOpenAfterBoundUpToTheLastListedOrderOfThePage() {
    UUID after = UUID.randomUUID(), first = UUID.randomUUID(), last = UUID.randomUUID();
    inRange(after, last);
    scan.triage(after, List.of(matching(first), matching(last)), tiers);
    verify(projections)
        .findByTenantIdAndOrderIdGreaterThanAndOrderIdLessThanEqualOrderByOrderId(
            tenant, after, last);
  }

  @Test
  void projectionsPastTheLastListedOrderAreJudgedTheSameWay() {
    UUID gone = UUID.randomUUID(), seenBefore = UUID.randomUUID();
    var fresh = recorded(gone, active, active);
    var known = recorded(seenBefore, active, recently);
    known.missing(recently);
    when(projections.findByTenantIdAndOrderIdGreaterThanOrderByOrderId(
            eq(tenant), eq(order), any()))
        .thenReturn(List.of(fresh, known));
    assertThat(scan.unlistedAfter(order, tiers))
        .extracting(Finding::orderId, Finding::verdict)
        .containsExactly(tuple(gone, CHANGED), tuple(seenBefore, CURRENT_HOT));
    verify(projections)
        .findByTenantIdAndOrderIdGreaterThanOrderByOrderId(tenant, order, PageRequest.of(0, 200));
  }
}
