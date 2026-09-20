package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesorder.domain.*;
import com.fabricmanagement.sales.salesorder.domain.port.*;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot;
import com.fabricmanagement.sales.salesorder.dto.*;
import com.fabricmanagement.sales.salesorder.infra.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderCoverServiceTest {
  static final UUID TENANT = UUID.randomUUID(), ORDER = UUID.randomUUID(), CASE = UUID.randomUUID();
  @Mock OrderCoverCaseRepository cases;
  @Mock OrderCoverCaseLineRepository caseLines;
  @Mock OrderCoverEvidenceRepository evidence;
  @Mock SalesOrderRepository orders;
  @Mock SalesOrderLineRepository lines;
  @Mock OrderCoverEvidenceService evidenceService;
  @Mock OrderCoverResultRepository results;
  @Mock OrderCoverLineResultRepository lineResults;
  @Mock ProductionOrderPort production;
  @Mock SalesOrderReservationPort reservations;
  @Mock com.fabricmanagement.common.infrastructure.events.DomainEventPublisher events;

  @Mock
  com.fabricmanagement.common.infrastructure.persistence.SalesOrderLineFulfilmentLock
      fulfilmentLock;

  OrderCoverService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    service =
        new OrderCoverService(
            cases,
            caseLines,
            evidence,
            orders,
            lines,
            evidenceService,
            results,
            lineResults,
            production,
            reservations,
            events,
            Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneOffset.UTC),
            fulfilmentLock);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void refusesIncompleteRequirementAsDurableDomainDecisionBeforeCreatingProduction() {
    Fixture fixture = fixture(false, BigDecimal.ONE);
    var decision = service.confirm(TENANT, ORDER, UUID.randomUUID(), fixture.payload("why"));
    assertThat(decision).isInstanceOf(OrderCoverCommandPort.Decision.Rejected.class);
    assertThat(((OrderCoverCommandPort.Decision.Rejected) decision).code())
        .isEqualTo("REQUIREMENT_COMPLETENESS_UNKNOWN");
    verifyNoInteractions(production, results, lineResults);
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderCoverEvidenceDto.Suitability.class,
      names = {"EXACT", "AMBIGUOUS"})
  void requiresRationaleWhenKnownShortfallIsZero(OrderCoverEvidenceDto.Suitability suitability) {
    Fixture fixture =
        fixture(true, OrderCoverEvidenceDto.Quantity.known(BigDecimal.ZERO, "kg"), suitability);
    var decision = service.confirm(TENANT, ORDER, UUID.randomUUID(), fixture.payload(null));
    assertThat(((OrderCoverCommandPort.Decision.Rejected) decision).code())
        .isEqualTo("RATIONALE_REQUIRED");
    verify(production, never()).requestDraftProductionOrder(any());
    verify(results, never()).saveAndFlush(any());
  }

  @Test
  void requiresRationaleWhenShortfallIsUnknown() {
    Fixture fixture =
        fixture(
            true,
            OrderCoverEvidenceDto.Quantity.unknown("kg", "SUITABILITY_EVIDENCE_UNKNOWN"),
            OrderCoverEvidenceDto.Suitability.UNKNOWN);
    var decision = service.confirm(TENANT, ORDER, UUID.randomUUID(), fixture.payload(null));
    assertThat(((OrderCoverCommandPort.Decision.Rejected) decision).code())
        .isEqualTo("RATIONALE_REQUIRED");
    verify(production).hasActiveProduction(TENANT, fixture.lineId());
    verify(production, never()).requestDraftProductionOrder(any());
    verifyNoInteractions(results, lineResults);
  }

  @Test
  void rationaleAllowsHumanProductionDecisionWhenKnownShortfallIsZero() {
    Fixture fixture = fixture(true, BigDecimal.ZERO);
    UUID receiptId = UUID.randomUUID();
    when(results.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              OrderCoverResult value = invocation.getArgument(0);
              value.setId(receiptId);
              return value;
            });
    when(production.requestDraftProductionOrder(any())).thenReturn(UUID.randomUUID());

    assertThat(
            service.confirm(
                TENANT, ORDER, UUID.randomUUID(), fixture.payload("customer requested production")))
        .isEqualTo(new OrderCoverCommandPort.Decision.Accepted(receiptId, Set.of()));
    verify(lineResults)
        .save(
            argThat(
                line ->
                    line.getSuitabilityAtDecision() == OrderCoverEvidenceDto.Suitability.EXACT));
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderCoverEvidenceDto.Suitability.class,
      names = {"EXACT", "AMBIGUOUS", "NO_MATCH"})
  void knownPositiveShortfallDoesNotRequireRationale(
      OrderCoverEvidenceDto.Suitability suitability) {
    Fixture fixture =
        fixture(true, OrderCoverEvidenceDto.Quantity.known(BigDecimal.ONE, "kg"), suitability);
    UUID receiptId = UUID.randomUUID();
    when(results.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              OrderCoverResult value = invocation.getArgument(0);
              value.setId(receiptId);
              return value;
            });
    when(production.requestDraftProductionOrder(any())).thenReturn(UUID.randomUUID());

    assertThat(service.confirm(TENANT, ORDER, UUID.randomUUID(), fixture.payload(null)))
        .isEqualTo(new OrderCoverCommandPort.Decision.Accepted(receiptId, Set.of()));
  }

  @Test
  void activeReservationCannotBeOverriddenByRationale() {
    Fixture fixture = fixture(true, BigDecimal.ZERO);
    when(reservations.hasActiveReservation(fixture.lineId())).thenReturn(true);

    var decision =
        service.confirm(TENANT, ORDER, UUID.randomUUID(), fixture.payload("make anyway"));

    assertThat(((OrderCoverCommandPort.Decision.Rejected) decision).code())
        .isEqualTo("ACTIVE_RESERVATION_EXISTS");
    verifyNoInteractions(production, results, lineResults);
  }

  @Test
  void activeProductionCannotBeOverriddenByRationale() {
    Fixture fixture = fixture(true, BigDecimal.ZERO);
    when(production.hasActiveProduction(TENANT, fixture.lineId())).thenReturn(true);

    var decision =
        service.confirm(TENANT, ORDER, UUID.randomUUID(), fixture.payload("make anyway"));

    assertThat(((OrderCoverCommandPort.Decision.Rejected) decision).code())
        .isEqualTo("ACTIVE_PRODUCTION_EXISTS");
    verify(production, never()).requestDraftProductionOrder(any());
    verifyNoInteractions(results, lineResults);
  }

  @Test
  void knownPositiveShortfallCreatesOneProfileBoundDraftForFullOpenQuantity() {
    Fixture fixture = fixture(true, BigDecimal.ONE);
    UUID receiptId = UUID.randomUUID(), workOrderId = UUID.randomUUID();
    when(results.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              OrderCoverResult value = invocation.getArgument(0);
              value.setId(receiptId);
              return value;
            });
    when(production.requestDraftProductionOrder(any())).thenReturn(workOrderId);
    var decision = service.confirm(TENANT, ORDER, UUID.randomUUID(), fixture.payload(null));
    assertThat(decision)
        .isEqualTo(new OrderCoverCommandPort.Decision.Accepted(receiptId, Set.of()));
    ArgumentCaptor<DraftProductionOrderCommand> command =
        ArgumentCaptor.forClass(DraftProductionOrderCommand.class);
    verify(production).requestDraftProductionOrder(command.capture());
    assertThat(command.getValue().plannedQty()).isEqualByComparingTo("10");
    assertThat(command.getValue().salesOrderId()).isEqualTo(ORDER);
    assertThat(command.getValue().requirementProfileId()).isNotNull();
    verify(lineResults).save(argThat(line -> "MAKE_TO_ORDER".equals(line.getOutcome())));
    var locked = inOrder(orders, cases, caseLines, lines, fulfilmentLock, evidenceService);
    locked.verify(orders).lockByTenantIdAndId(TENANT, ORDER);
    locked.verify(cases).lock(TENANT, CASE);
    locked.verify(caseLines).lockAll(TENANT, CASE);
    locked.verify(lines).lockAllForOrder(TENANT, ORDER);
    locked.verify(fulfilmentLock).lockAll(TENANT, List.of(fixture.lineId()));
    locked.verify(evidenceService).revalidate(ORDER, fixture.evidenceId());
  }

  private Fixture fixture(boolean complete, BigDecimal shortfall) {
    return fixture(
        complete,
        OrderCoverEvidenceDto.Quantity.known(shortfall, "kg"),
        OrderCoverEvidenceDto.Suitability.EXACT);
  }

  private Fixture fixture(
      boolean complete,
      OrderCoverEvidenceDto.Quantity shortfall,
      OrderCoverEvidenceDto.Suitability suitability) {
    UUID lineId = UUID.randomUUID(), evidenceId = UUID.randomUUID();
    SalesOrder order = mock(SalesOrder.class);
    when(order.getCoverRegime()).thenReturn(OrderCoverRegime.GOVERNED);
    when(order.getStatus()).thenReturn(OrderStatus.CONFIRMED);
    when(order.getIsActive()).thenReturn(true);
    lenient().when(order.getTradingPartnerId()).thenReturn(UUID.randomUUID());
    OrderCoverCase cover = mock(OrderCoverCase.class);
    when(cover.getId()).thenReturn(CASE);
    when(cover.getSalesOrderId()).thenReturn(ORDER);
    when(cover.getState()).thenReturn(OrderCoverCaseState.OPEN);
    OrderCoverCaseLine scope = mock(OrderCoverCaseLine.class);
    when(scope.getSalesOrderLineId()).thenReturn(lineId);
    when(scope.unresolved()).thenReturn(true, false);
    SalesOrderLine line = mock(SalesOrderLine.class);
    when(line.getId()).thenReturn(lineId);
    lenient().when(line.getRequestedQty()).thenReturn(BigDecimal.TEN);
    lenient().when(line.getShippedQty()).thenReturn(BigDecimal.ZERO);
    lenient().when(line.getLineStatus()).thenReturn(SalesOrderLineStatus.PENDING);
    RequirementProfileSnapshot profile = complete ? mock(RequirementProfileSnapshot.class) : null;
    if (complete) {
      when(profile.complete()).thenReturn(true);
      lenient().when(profile.profileId()).thenReturn(UUID.randomUUID());
      lenient().when(profile.profileVersion()).thenReturn(1);
      lenient().when(profile.facets()).thenReturn(List.of());
    }
    when(line.getRequirementProfileSnapshot()).thenReturn(profile);
    lenient().when(line.getUnit()).thenReturn("kg");
    lenient().when(line.getCurrency()).thenReturn("USD");
    OrderCoverEvidenceDto.Quantity requested =
        OrderCoverEvidenceDto.Quantity.known(BigDecimal.TEN, "kg");
    OrderCoverEvidenceDto.Line lineEvidence =
        new OrderCoverEvidenceDto.Line(
            lineId,
            0,
            null,
            requested,
            requested,
            requested,
            shortfall,
            suitability,
            List.of(),
            List.of(),
            List.of(),
            List.of());
    OrderCoverEvidence snapshot = mock(OrderCoverEvidence.class);
    when(snapshot.getId()).thenReturn(evidenceId);
    when(snapshot.getCaseId()).thenReturn(CASE);
    when(snapshot.getRevision()).thenReturn(1L);
    when(snapshot.getLines()).thenReturn(List.of(lineEvidence));
    when(orders.lockByTenantIdAndId(TENANT, ORDER)).thenReturn(Optional.of(order));
    when(cases.lock(TENANT, CASE)).thenReturn(Optional.of(cover));
    when(evidence.findByTenantIdAndSalesOrderIdAndId(TENANT, ORDER, evidenceId))
        .thenReturn(Optional.of(snapshot));
    when(evidence.findFirstByTenantIdAndCaseIdOrderByRevisionDesc(TENANT, CASE))
        .thenReturn(Optional.of(snapshot));
    when(evidenceService.revalidate(ORDER, evidenceId))
        .thenReturn(new OrderCoverEvidenceService.Revalidation(true, "x"));
    when(caseLines.lockAll(TENANT, CASE)).thenReturn(List.of(scope));
    when(lines.lockAllForOrder(TENANT, ORDER)).thenReturn(List.of(line));
    return new Fixture(lineId, evidenceId);
  }

  record Fixture(UUID lineId, UUID evidenceId) {
    ConfirmProductionCoverPayload payload(String rationale) {
      return new ConfirmProductionCoverPayload(
          CASE, evidenceId, 1, List.of(lineId), rationale, null);
    }
  }
}
