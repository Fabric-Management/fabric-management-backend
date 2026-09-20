package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesorder.domain.*;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverEvidencePort;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverEvidencePort.*;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacet;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileBasis;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileInput;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto.*;
import com.fabricmanagement.sales.salesorder.infra.repository.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotSerializeTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class OrderCoverEvidenceServiceTest {
  private final UUID tenant = UUID.randomUUID();
  private final UUID order = UUID.randomUUID();
  private final UUID caseId = UUID.randomUUID();
  private final UUID product = UUID.randomUUID();
  private final Instant now = Instant.parse("2026-09-15T12:00:00Z");

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void conservesOneHundredAcrossTwoEightyLinesAndOrdersDeterministically() {
    var first = requirement(1, "80", true);
    var second = requirement(2, "80", true);
    var rows = evaluate(List.of(second, first), List.of(lot(1, "100", Eligibility.ELIGIBLE)));
    assertThat(rows).extracting(Line::lineId).containsExactly(first.lineId(), second.lineId());
    assertThat(rows).allSatisfy(row -> assertThat(row.suitableFree().value()).isEqualTo("100"));
    assertThat(rows.getFirst().remainingSuitableFree().value()).isEqualTo("100");
    assertThat(rows.getLast().remainingSuitableFree().value()).isEqualTo("20");
    assertThat(rows.getLast().competingAllocations())
        .containsExactly(
            new CompetingAllocation(first.lineId(), Quantity.known(new BigDecimal("80"), "KG")));
    assertThat(rows.getLast().shortfall().value()).isEqualTo("60");
    assertThat(rows.getLast().suitability()).isEqualTo(Suitability.EXACT);
    assertThat(rows.getLast().blockingReasons())
        .contains("INSUFFICIENT_SUITABLE_STOCK", "COMPETING_LINE_ALLOCATION")
        .doesNotContain("NO_SUITABLE_STOCK");
    assertThat(rows.getLast().controlReasons())
        .contains("CHECK_PRODUCTION_CAPACITY", "CONFIRM_DELIVERY_DATE");
  }

  @Test
  void stableIdBreaksEqualCreationTimeAndFractionalCapacityIsConserved() {
    var rows =
        evaluate(
            List.of(requirement(2, "0.8", true), requirement(1, "0.8", true)),
            List.of(lot(1, "1.0", Eligibility.ELIGIBLE)));
    assertThat(rows.getFirst().suitableFree().value()).isEqualTo("1");
    assertThat(rows.getLast().suitableFree().value()).isEqualTo("1");
    assertThat(rows.getLast().remainingSuitableFree().value()).isEqualTo("0.2");
    assertThat(rows.getLast().shortfall().value()).isEqualTo("0.6");
  }

  @Test
  void multipleEligibleSourcesRequireControlEvenWhenFirstAloneSuffices() {
    var rows =
        evaluate(
            List.of(requirement(1, "50", true)),
            List.of(lot(1, "100", Eligibility.ELIGIBLE), lot(2, "100", Eligibility.ELIGIBLE)));
    assertThat(rows.getFirst().suitability()).isEqualTo(Suitability.AMBIGUOUS);
    assertThat(rows.getFirst().controlReasons()).contains("VERIFY_PHYSICAL_STOCK");
  }

  @Test
  void suggestedAllocationCannotTurnLaterAmbiguousLineIntoAutomaticCandidate() {
    var rows =
        evaluate(
            List.of(requirement(1, "100", true), requirement(2, "80", true)),
            List.of(lot(1, "100", Eligibility.ELIGIBLE), lot(2, "100", Eligibility.ELIGIBLE)));
    assertThat(rows)
        .allSatisfy(row -> assertThat(row.suitability()).isEqualTo(Suitability.AMBIGUOUS));
  }

  @Test
  void exactSingleSourceSufficientIsAnAutomaticCandidateOnly() {
    var row =
        evaluate(List.of(requirement(1, "50", true)), List.of(lot(1, "100", Eligibility.ELIGIBLE)))
            .getFirst();
    assertThat(row.suitability()).isEqualTo(Suitability.EXACT);
    assertThat(row.shortfall().value()).isEqualTo("0");
    assertThat(row.blockingReasons()).isEmpty();
    assertThat(row.suitableFree().value()).isEqualTo("100");
    assertThat(row.remainingSuitableFree().value()).isEqualTo("100");
  }

  @Test
  void fullySuggestedStockRemainsExactAndExplainsTheCompetingLine() {
    var first = requirement(1, "100", true);
    var rows =
        evaluate(
            List.of(first, requirement(2, "80", true)),
            List.of(lot(1, "100", Eligibility.ELIGIBLE)));
    var second = rows.getLast();
    assertThat(second.suitability()).isEqualTo(Suitability.EXACT);
    assertThat(second.suitableFree().value()).isEqualTo("100");
    assertThat(second.remainingSuitableFree().value()).isEqualTo("0");
    assertThat(second.shortfall().value()).isEqualTo("80");
    assertThat(second.competingAllocations())
        .containsExactly(
            new CompetingAllocation(first.lineId(), Quantity.known(new BigDecimal("100"), "KG")));
    assertThat(second.blockingReasons())
        .contains("COMPETING_LINE_ALLOCATION")
        .doesNotContain("NO_SUITABLE_STOCK");
  }

  @Test
  void fullySuggestedMultipleSourcesRemainAmbiguousAndPriorAllocationsAreAggregated() {
    var first = requirement(1, "200", true);
    var rows =
        evaluate(
            List.of(first, requirement(2, "80", true)),
            List.of(lot(1, "100", Eligibility.ELIGIBLE), lot(2, "100", Eligibility.ELIGIBLE)));
    var second = rows.getLast();
    assertThat(second.suitability()).isEqualTo(Suitability.AMBIGUOUS);
    assertThat(second.suitableFree().value()).isEqualTo("200");
    assertThat(second.remainingSuitableFree().value()).isEqualTo("0");
    assertThat(second.competingAllocations())
        .containsExactly(
            new CompetingAllocation(first.lineId(), Quantity.known(new BigDecimal("200"), "KG")));
    assertThat(second.blockingReasons())
        .contains("MULTIPLE_ELIGIBLE_LOTS", "COMPETING_LINE_ALLOCATION")
        .doesNotContain("NO_SUITABLE_STOCK");
  }

  @Test
  void competitionWithoutShortageIsExplainedButDoesNotBlockExactCandidate() {
    var rows =
        evaluate(
            List.of(requirement(1, "80", true), requirement(2, "10", true)),
            List.of(lot(1, "100", Eligibility.ELIGIBLE)));
    var second = rows.getLast();
    assertThat(second.suitableFree().value()).isEqualTo("100");
    assertThat(second.remainingSuitableFree().value()).isEqualTo("20");
    assertThat(second.shortfall().value()).isEqualTo("0");
    assertThat(second.competingAllocations()).hasSize(1);
    assertThat(second.blockingReasons()).isEmpty();
  }

  @Test
  void everyKnownLineExplainsStockDifferenceAndSuggestionsConserveTheSharedPool() {
    var requirements =
        List.of(requirement(1, "30", true), requirement(2, "40", true), requirement(3, "80", true));
    var rows = evaluate(requirements, List.of(lot(1, "100", Eligibility.ELIGIBLE)));
    BigDecimal suggested = BigDecimal.ZERO;
    for (Line row : rows) {
      BigDecimal prior =
          row.competingAllocations().stream()
              .map(value -> new BigDecimal(value.quantity().value()))
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      assertThat(new BigDecimal(row.suitableFree().value()).subtract(prior))
          .isEqualByComparingTo(row.remainingSuitableFree().value());
      suggested =
          suggested.add(
              new BigDecimal(row.requested().value())
                  .subtract(new BigDecimal(row.shortfall().value())));
    }
    assertThat(suggested).isEqualByComparingTo("100");
  }

  @Test
  void unknownNeverBecomesZeroOrShortfallAndTaintsCompetingDemand() {
    var rows =
        evaluate(
            List.of(requirement(1, "80", false), requirement(2, "80", true)),
            List.of(lot(1, "100", Eligibility.ELIGIBLE)));
    assertThat(rows.getFirst().suitability()).isEqualTo(Suitability.UNKNOWN);
    assertThat(rows.getFirst().suitableFree().state()).isEqualTo(Knowledge.UNKNOWN);
    assertThat(rows.getLast().suitability()).isEqualTo(Suitability.EXACT);
    assertThat(rows.getLast().suitableFree().value()).isEqualTo("100");
    assertThat(rows)
        .allSatisfy(
            row -> {
              assertThat(row.remainingSuitableFree().state()).isEqualTo(Knowledge.UNKNOWN);
              assertThat(row.shortfall().state()).isEqualTo(Knowledge.UNKNOWN);
              assertThat(row.shortfall().value()).isNull();
            });
    assertThat(rows.getLast().blockingReasons()).contains("COMPETING_REQUIREMENT_UNKNOWN");
  }

  @Test
  void unknownCandidateCannotEstablishShortfall() {
    var row =
        evaluate(List.of(requirement(1, "80", true)), List.of(lot(1, "100", Eligibility.UNKNOWN)))
            .getFirst();
    assertThat(row.shortfall().value()).isNull();
    assertThat(row.suitableFree().state()).isEqualTo(Knowledge.UNKNOWN);
  }

  @Test
  void excludedStockRetainsSourcesAndKnownZeroOnlyWithCompleteRequirements() {
    var row =
        evaluate(List.of(requirement(1, "80", true)), List.of(lot(1, "0", Eligibility.EXCLUDED)))
            .getFirst();
    assertThat(row.suitableFree().value()).isEqualTo("0");
    assertThat(row.shortfall().value()).isEqualTo("80");
    assertThat(row.sources()).extracting(Source::type).contains("BATCH");
    assertThat(row.suitability()).isEqualTo(Suitability.NO_MATCH);
    assertThat(row.blockingReasons()).contains("NO_SUITABLE_STOCK", "INSUFFICIENT_SUITABLE_STOCK");
  }

  @Test
  void noCandidatesWithKnownCanonicalDemandProvesShortfall() {
    var row = evaluate(List.of(requirement(1, "80", true)), List.of()).getFirst();
    assertThat(row.shortfall().value()).isEqualTo("80");
    assertThat(row.suitability()).isEqualTo(Suitability.NO_MATCH);
    assertThat(row.blockingReasons()).contains("NO_SUITABLE_STOCK", "INSUFFICIENT_SUITABLE_STOCK");
  }

  @Test
  void unsupportedConversionDoesNotInferProduction() {
    var line = requirement(1, "80", true);
    var rows =
        OrderCoverEvidenceEvaluator.evaluate(
            requirements(List.of(line)),
            new Inputs(
                List.of(new Demand(line.lineId(), null, "KG", "UNSUPPORTED_UNIT_CONVERSION")),
                List.of()));
    assertThat(rows.getFirst().shortfall().value()).isNull();
    assertThat(rows.getFirst().blockingReasons()).contains("UNSUPPORTED_UNIT_CONVERSION");
  }

  @Test
  void independentProductsDoNotShareCapacity() {
    var first = requirement(1, "80", true);
    var otherProduct = UUID.randomUUID();
    var second =
        new Requirement(
            new UUID(0, 2),
            1,
            otherProduct,
            now,
            new BigDecimal("80"),
            "KG",
            true,
            null,
            "typed",
            null);
    var otherLot =
        new Lot(
            new UUID(0, 12),
            otherProduct,
            "KG",
            new BigDecimal("100"),
            new BigDecimal("100"),
            Eligibility.ELIGIBLE,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "source");
    var rows =
        evaluate(List.of(first, second), List.of(lot(1, "100", Eligibility.ELIGIBLE), otherLot));
    assertThat(rows)
        .allSatisfy(
            row -> {
              assertThat(row.suitableFree().value()).isEqualTo("100");
              assertThat(row.remainingSuitableFree().value()).isEqualTo("100");
              assertThat(row.competingAllocations()).isEmpty();
            });
  }

  @Test
  void freeTextRemainsMissingEvidence() {
    var line =
        new Requirement(
            new UUID(0, 1),
            0,
            null,
            now,
            BigDecimal.ONE,
            "KG",
            false,
            "REQUIREMENT_COMPLETENESS_UNKNOWN",
            "text",
            null);
    assertThat(evaluate(List.of(line), List.of()).getFirst().blockingReasons())
        .contains("PRODUCT_REQUIREMENT_MISSING");
  }

  @Test
  void candidateCountDoesNotImposePageBoundary() {
    var lots =
        java.util.stream.IntStream.rangeClosed(1, 125)
            .mapToObj(id -> lot(id, "1", Eligibility.ELIGIBLE))
            .toList();
    var row = evaluate(List.of(requirement(1, "150", true)), lots).getFirst();
    assertThat(row.suitableFree().value()).isEqualTo("125");
    assertThat(row.shortfall().value()).isEqualTo("25");
  }

  @Test
  void duplicatePhysicalLotIsRejectedInsteadOfSpentTwice() {
    var lot = lot(1, "100", Eligibility.ELIGIBLE);
    assertThatThrownBy(() -> evaluate(List.of(requirement(1, "80", true)), List.of(lot, lot)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fingerprintNormalisesDecimalScaleMapOrderAndSourceObservationTime() {
    var first = new Source("BATCH", product, 1L, now, now, SourceKnowledge.VERIFIED, null);
    var later =
        new Source("BATCH", product, 1L, now.plusSeconds(60), now, SourceKnowledge.VERIFIED, null);
    assertThat(
            OrderCoverFingerprint.of(Map.of("source", first, "quantity", new BigDecimal("100.00"))))
        .isEqualTo(
            OrderCoverFingerprint.of(Map.of("quantity", new BigDecimal("100"), "source", later)));
    assertThat(OrderCoverFingerprint.of(new BigDecimal("12345678901234567890.123456789")))
        .isNotEqualTo(OrderCoverFingerprint.of(new BigDecimal("12345678901234567890.123456788")));
  }

  @Test
  void sourceRevisionChangeInvalidatesEvenWithIdenticalTotals() {
    var before = new Source("BATCH", product, 1L, null, now, SourceKnowledge.VERIFIED, null);
    var after = new Source("BATCH", product, 2L, null, now, SourceKnowledge.VERIFIED, null);
    assertThat(OrderCoverFingerprint.of(before)).isNotEqualTo(OrderCoverFingerprint.of(after));
    assertThat(OrderCoverFingerprint.of(Map.of("attribute", new BigDecimal("100"))))
        .isNotEqualTo(OrderCoverFingerprint.of(Map.of("attribute", "100")));
  }

  @Test
  void liveLoaderKeepsEmptySpecsUnknownAndRebuildAppendsWithoutEditingHistory() {
    TenantContext.setCurrentTenantId(tenant);
    TenantContext.setCurrentUserId(UUID.randomUUID());
    var orders = mock(SalesOrderRepository.class);
    var lines = mock(SalesOrderLineRepository.class);
    var streams = mock(OrderCoverEvidenceStreamRepository.class);
    var snapshots = mock(OrderCoverEvidenceRepository.class);
    var port = mock(OrderCoverEvidencePort.class);
    var service =
        new OrderCoverEvidenceService(
            orders,
            lines,
            streams,
            snapshots,
            port,
            Clock.fixed(now, ZoneOffset.UTC),
            transactions(),
            mock(OrderCoverObjectAccess.class));
    var salesOrder = SalesOrder.builder().build();
    salesOrder.setId(order);
    salesOrder.setTenantId(tenant);
    var line =
        SalesOrderLine.builder()
            .salesOrderId(order)
            .productId(product)
            .requestedQty(new BigDecimal("80"))
            .unit("KG")
            .build();
    line.setId(new UUID(0, 1));
    line.setCreatedAt(now);
    when(orders.findByTenantIdAndId(tenant, order)).thenReturn(Optional.of(salesOrder));
    when(lines.findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByCreatedAtAscIdAsc(tenant, order))
        .thenReturn(List.of(line));
    var stream = OrderCoverEvidenceStream.create(tenant, order, caseId);
    when(streams.lockScope(tenant, caseId)).thenReturn(Optional.empty(), Optional.of(stream));
    List<Requirements> inspected = new ArrayList<>();
    when(port.inspect(any()))
        .thenAnswer(
            call -> {
              Requirements requirements = call.getArgument(0);
              inspected.add(requirements);
              return inputs(requirements.lines(), List.of(lot(1, "100", Eligibility.ELIGIBLE)));
            });
    List<OrderCoverEvidence> saved = new ArrayList<>();
    when(snapshots.findFirstByTenantIdAndCaseIdOrderByRevisionDesc(tenant, caseId))
        .thenAnswer(call -> saved.isEmpty() ? Optional.empty() : Optional.of(saved.getLast()));
    when(snapshots.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              OrderCoverEvidence value = call.getArgument(0);
              value.setId(UUID.randomUUID());
              saved.add(value);
              return value;
            });
    var first = service.refresh(order, caseId);
    var sequence = inOrder(streams, orders, port);
    sequence.verify(streams).lockScope(tenant, caseId);
    sequence
        .verify(streams)
        .establishScope(any(UUID.class), eq(tenant), eq(order), eq(caseId), anyString(), any());
    sequence.verify(streams).lockScope(tenant, caseId);
    sequence.verify(orders).findByTenantIdAndId(tenant, order);
    sequence.verify(port).inspect(any());
    var same = service.refresh(order, caseId);
    var rebuilt = service.rebuild(order, caseId);
    assertThat(same.id()).isEqualTo(first.id());
    assertThat(rebuilt.id()).isNotEqualTo(first.id());
    assertThat(rebuilt.revision()).isEqualTo(2);
    assertThat(first.revision()).isEqualTo(1);
    assertThat(rebuilt.inputFingerprint()).isEqualTo(first.inputFingerprint());
    assertThat(first.lines().getFirst().blockingReasons())
        .contains("REQUIREMENT_COMPLETENESS_UNKNOWN");
    assertThat(inspected.getFirst().lines().getFirst().complete()).isFalse();
    line.setModuleSpecs(Map.of("width", 160));
    var changed = service.refresh(order, caseId);
    assertThat(changed.inputFingerprint()).isNotEqualTo(first.inputFingerprint());
    assertThat(changed.lines().getFirst().blockingReasons()).contains("UNTYPED_REQUIREMENTS");
    line.setModuleSpecs(null);
    RequirementProfileSnapshot typed = completeProfile();
    line.attachRequirementProfile(typed);
    var resolved = service.refresh(order, caseId);
    Requirement typedRequirement = inspected.getLast().lines().getFirst();
    assertThat(typedRequirement.complete()).isTrue();
    assertThat(typedRequirement.profile())
        .isEqualTo(RequirementEvidenceProfileMapper.toPort(typed));
    assertThat(typedRequirement.requirementFingerprint()).isEqualTo(typed.fingerprint());
    assertThat(resolved.lines().getFirst().suitability()).isEqualTo(Suitability.EXACT);
    assertThat(saved).hasSize(4);
  }

  @Test
  void caseIdentityCannotBeReboundToAnotherOrder() {
    var stream = OrderCoverEvidenceStream.create(tenant, order, caseId);
    assertThatThrownBy(() -> stream.requireOrder(UUID.randomUUID()))
        .isInstanceOf(com.fabricmanagement.sales.common.exception.OrderDomainException.class);
  }

  @Test
  void historicalLookupAlwaysIncludesAuthenticatedTenantAndOrder() {
    TenantContext.setCurrentTenantId(tenant);
    TenantContext.setCurrentUserId(UUID.randomUUID());
    var repository = mock(OrderCoverEvidenceRepository.class);
    UUID evidenceId = UUID.randomUUID();
    when(repository.findByTenantIdAndSalesOrderIdAndId(tenant, order, evidenceId))
        .thenReturn(Optional.empty());
    var service =
        new OrderCoverEvidenceService(
            null,
            null,
            null,
            repository,
            null,
            Clock.systemUTC(),
            transactions(),
            mock(OrderCoverObjectAccess.class));
    assertThatThrownBy(() -> service.read(order, evidenceId))
        .isInstanceOf(com.fabricmanagement.sales.common.exception.OrderDomainException.class);
    verify(repository).findByTenantIdAndSalesOrderIdAndId(tenant, order, evidenceId);
  }

  @Test
  void serializationFailureRollsBackAndRetriesInFreshBoundedTransaction() {
    TenantContext.setCurrentTenantId(tenant);
    TenantContext.setCurrentUserId(UUID.randomUUID());
    var streams = mock(OrderCoverEvidenceStreamRepository.class);
    var orders = mock(SalesOrderRepository.class);
    var lines = mock(SalesOrderLineRepository.class);
    var snapshots = mock(OrderCoverEvidenceRepository.class);
    var port = mock(OrderCoverEvidencePort.class);
    var transactions = transactions();
    var failure =
        new CannotSerializeTransactionException("retry", new SQLException("race", "40001"));
    when(streams.lockScope(tenant, caseId))
        .thenThrow(failure)
        .thenReturn(Optional.of(OrderCoverEvidenceStream.create(tenant, order, caseId)));
    when(orders.findByTenantIdAndId(tenant, order))
        .thenReturn(Optional.of(SalesOrder.builder().build()));
    when(port.inspect(any())).thenReturn(new Inputs(List.of(), List.of()));
    when(snapshots.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              OrderCoverEvidence snapshot = call.getArgument(0);
              snapshot.setId(UUID.randomUUID());
              return snapshot;
            });
    var service =
        new OrderCoverEvidenceService(
            orders,
            lines,
            streams,
            snapshots,
            port,
            Clock.fixed(now, ZoneOffset.UTC),
            transactions,
            mock(OrderCoverObjectAccess.class));
    assertThat(service.refresh(order, caseId).revision()).isEqualTo(1);
    verify(transactions, times(2))
        .getTransaction(
            argThat(
                definition ->
                    definition.getPropagationBehavior()
                            == TransactionDefinition.PROPAGATION_REQUIRES_NEW
                        && definition.getIsolationLevel()
                            == TransactionDefinition.ISOLATION_REPEATABLE_READ
                        && definition.getTimeout() == 30));
    verify(transactions).rollback(any());
    verify(transactions).commit(any());
    verify(port).inspect(any());
  }

  @Test
  void retryIsBoundedAndDoesNotRetryBusinessFailures() {
    TenantContext.setCurrentTenantId(tenant);
    TenantContext.setCurrentUserId(UUID.randomUUID());
    var streams = mock(OrderCoverEvidenceStreamRepository.class);
    var transactions = transactions();
    var service =
        new OrderCoverEvidenceService(
            null,
            null,
            streams,
            null,
            null,
            Clock.systemUTC(),
            transactions,
            mock(OrderCoverObjectAccess.class));
    when(streams.lockScope(tenant, caseId))
        .thenThrow(
            new CannotSerializeTransactionException("retry", new SQLException("race", "40001")));
    assertThatThrownBy(() -> service.refresh(order, caseId))
        .isInstanceOf(CannotSerializeTransactionException.class);
    verify(transactions, times(3)).rollback(any());
    verify(transactions, never()).commit(any());
    reset(streams, transactions);
    when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
    when(streams.lockScope(tenant, caseId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.rebuild(order, caseId))
        .isInstanceOf(com.fabricmanagement.sales.common.exception.OrderDomainException.class);
    verify(transactions).getTransaction(any());
    verify(streams, never()).establishScope(any(), any(), any(), any(), any(), any());
  }

  private PlatformTransactionManager transactions() {
    var manager = mock(PlatformTransactionManager.class);
    when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
    return manager;
  }

  private RequirementProfileSnapshot completeProfile() {
    UUID actor = UUID.randomUUID();
    RequirementFacet origin =
        new RequirementFacet(
            RequirementFacet.Kind.ORIGIN,
            "fabric_made",
            RequirementFacet.State.UNCONSTRAINED,
            RequirementFacet.Comparison.NONE,
            null,
            new RequirementFacet.DecisionBasis(
                RequirementFacet.DecisionBasis.Source.AUTHORISED_DECISION,
                "service-fixture",
                actor,
                now));
    RequirementProfileInput input =
        new RequirementProfileInput(
            new RequirementProfileBasis(
                RequirementProfileBasis.Kind.AUTHORISED_DECISION,
                product,
                null,
                null,
                null,
                actor,
                now,
                "service-fixture"),
            "FIXTURE_V1",
            "SALES_REQ_1_RESOLUTION_V1",
            Set.of(origin.identity()),
            List.of(origin),
            List.of(),
            List.of());
    return RequirementProfileSnapshot.resolve(UUID.randomUUID(), 1, input, null, false);
  }

  private Requirement requirement(int id, String quantity, boolean complete) {
    return new Requirement(
        new UUID(0, id),
        1,
        product,
        now,
        new BigDecimal(quantity),
        "KG",
        complete,
        complete ? null : "UNTYPED_REQUIREMENTS",
        "typed",
        null);
  }

  private Lot lot(int id, String quantity, Eligibility eligibility) {
    UUID lotId = new UUID(1, id);
    var source = new Source("BATCH", lotId, 1L, null, now, SourceKnowledge.VERIFIED, null);
    return new Lot(
        lotId,
        product,
        "KG",
        new BigDecimal(quantity),
        eligibility == Eligibility.UNKNOWN ? null : new BigDecimal(quantity),
        eligibility,
        List.of(),
        List.of(source),
        List.of(),
        List.of(),
        "source-" + id);
  }

  private Requirements requirements(List<Requirement> lines) {
    return new Requirements(tenant, order, caseId, 1, lines);
  }

  private Inputs inputs(List<Requirement> lines, List<Lot> lots) {
    return new Inputs(
        lines.stream()
            .map(line -> new Demand(line.lineId(), line.requested(), "KG", null))
            .toList(),
        lots);
  }

  private List<Line> evaluate(List<Requirement> lines, List<Lot> lots) {
    return OrderCoverEvidenceEvaluator.evaluate(requirements(lines), inputs(lines, lots));
  }
}
