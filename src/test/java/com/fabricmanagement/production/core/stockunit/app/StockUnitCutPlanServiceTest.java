package com.fabricmanagement.production.core.stockunit.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.batch.api.BatchLotQuantityIntentPort;
import com.fabricmanagement.production.core.stockunit.api.*;
import com.fabricmanagement.production.core.stockunit.api.StockUnitCutTargetPort.*;
import com.fabricmanagement.production.core.stockunit.domain.*;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitCutPlanRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;

class StockUnitCutPlanServiceTest {
  final UUID tenant = UUID.randomUUID(),
      actor = UUID.randomUUID(),
      document = UUID.randomUUID(),
      line = UUID.randomUUID(),
      product = UUID.randomUUID(),
      batch = UUID.randomUUID(),
      session = UUID.randomUUID();
  final StockUnitCutTargetPort targets = mock(StockUnitCutTargetPort.class);
  final StockUnitCutPlanRepository repository = mock(StockUnitCutPlanRepository.class);
  final StockUnitCutPlanSources sources = mock(StockUnitCutPlanSources.class);
  final StockUnitCutPlanGuard guard = mock(StockUnitCutPlanGuard.class);
  final StockUnitCutPlanLedger ledger = mock(StockUnitCutPlanLedger.class);
  final StockUnitCutPlanOperations operations = mock(StockUnitCutPlanOperations.class);
  final StockUnitSoftHoldPort holds = mock(StockUnitSoftHoldPort.class);
  final BatchLotQuantityIntentPort intents = mock(BatchLotQuantityIntentPort.class);
  final PieceAllocationPort allocations = mock(PieceAllocationPort.class);
  final EntityManager entityManager = mock(EntityManager.class);
  final StockUnitCutDraftEvidence evidence = mock(StockUnitCutDraftEvidence.class);
  final Clock clock = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC);
  final StockUnitCutPlanService service =
      new StockUnitCutPlanService(
          targets,
          repository,
          sources,
          guard,
          new StockUnitCutPlanViews(sources, ledger, targets),
          ledger,
          operations,
          holds,
          intents,
          allocations,
          entityManager,
          clock,
          evidence);
  final Context context =
      new Context(
          new Target(TargetType.SALES_ORDER, document, line),
          product,
          new BigDecimal("2050"),
          "unchanged-demand",
          true,
          true,
          false,
          true,
          true,
          "/sales/" + document,
          "SO-2026-001",
          0L);
  final List<StockUnitCutPlan.Source> rolls =
      IntStream.range(0, 17)
          .mapToObj(
              index ->
                  new StockUnitCutPlan.Source(
                      UUID.randomUUID(),
                      batch,
                      0L,
                      new BigDecimal("125"),
                      new BigDecimal("30"),
                      "KG",
                      "ROLL-" + index,
                      null,
                      0L))
          .toList();
  final List<UUID> ids = rolls.stream().map(StockUnitCutPlan.Source::stockUnitId).toList();

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    when(sources.capture(ids, line, session)).thenReturn(rolls);
    when(targets.sourcesCompatible(context.target(), ids)).thenReturn(true);
    when(sources.current(any())).thenReturn(true);
    when(sources.ownsSources(any())).thenReturn(true);
    when(repository.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              StockUnitCutPlan plan = call.getArgument(0);
              plan.setId(UUID.randomUUID());
              plan.setTenantId(tenant);
              return plan;
            });
    when(operations.complete(any(), any(), any(), anyString(), any()))
        .thenAnswer(call -> call.getArgument(4));
  }

  @AfterEach
  void cleanup() {
    TenantContext.clear();
  }

  @Test
  void applyHoldsAllSourcesAndKeepsTheExactCommercialQuantityWithoutCutting() {
    var result =
        service.apply(context, ids, ids.getLast(), session, UUID.randomUUID(), actor, "request");
    assertThat(result.deliveryQuantity()).isEqualByComparingTo("2050");
    assertThat(result.sourceQuantity()).isEqualByComparingTo("2125");
    assertThat(result.cutDeliveryQuantity()).isEqualByComparingTo("50");
    assertThat(result.remainderQuantity()).isEqualByComparingTo("75");
    assertThat(result.actual()).isNull();
    assertThat(result.deliveryStockUnitId()).isNull();
    verify(holds)
        .replaceOrderHolds(
            eq(document),
            eq("SO-2026-001"),
            eq(line),
            eq(actor),
            eq(session),
            eq(ids),
            argThat(
                lots ->
                    lots.size() == 1
                        && lots.iterator().next().quantity().compareTo(new BigDecimal("2125"))
                            == 0));
    verifyNoInteractions(ledger, allocations);
  }

  @Test
  void replayDoesNotReplaceHoldsOrCreateAnotherPlan() {
    var first =
        service.apply(context, ids, ids.getLast(), session, UUID.randomUUID(), actor, "request");
    clearInvocations(repository, sources, holds, targets, evidence);
    UUID operation = UUID.randomUUID();
    when(operations.replay(operation, actor, line, "same-body")).thenReturn(first);
    assertThat(service.apply(context, ids, ids.getLast(), session, operation, actor, "same-body"))
        .isSameAs(first);
    verifyNoInteractions(repository, sources, holds, targets, evidence);
  }

  @Test
  void foreignOwnershipLeavesTheOldPlanAndItsSourcesUntouched() {
    doThrow(
            new com.fabricmanagement.production.core.stockunit.domain.exception
                .StockUnitHeldException())
        .when(sources)
        .capture(ids, line, session);
    assertThatThrownBy(
            () ->
                service.apply(
                    context, ids, ids.getLast(), session, UUID.randomUUID(), actor, "request"))
        .isInstanceOf(
            com.fabricmanagement.production.core.stockunit.domain.exception.StockUnitHeldException
                .class);
    verifyNoInteractions(holds, repository, ledger, allocations);
  }

  @Test
  void cutApplyRequiresTheServerEvaluationOfTheCurrentMeasurementEvidence() {
    doThrow(
            com.fabricmanagement.production.core.stockunit.domain.exception
                .StockUnitCutPlanException.conflict(
                "EVALUATION_REQUIRED", "Source measurements changed"))
        .when(evidence)
        .assertCurrent(context, rolls, ids.getLast(), session, actor);
    assertThatThrownBy(
            () ->
                service.apply(
                    context, ids, ids.getLast(), session, UUID.randomUUID(), actor, "request"))
        .hasMessageContaining("measurements changed");
    verifyNoInteractions(holds, repository, allocations);
  }
}
