package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.SalesOrderLineFulfilmentLock;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverCase;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverCaseLine;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverCaseState;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverEvidence;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverRegime;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverResult;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverCommandPort;
import com.fabricmanagement.sales.salesorder.domain.port.ProductionOrderPort;
import com.fabricmanagement.sales.salesorder.domain.port.SalesOrderReservationPort;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot;
import com.fabricmanagement.sales.salesorder.dto.ConfirmProductionCoverPayload;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderCoverCaseLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderCoverCaseRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderCoverEvidenceRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderCoverLineResultRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderCoverResultRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrderCoverUnknownSuitabilityTest {
  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ORDER = UUID.randomUUID();
  private static final UUID CASE = UUID.randomUUID();

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void unknownSuitabilityWithRationaleCreatesAReceiptAndDraftReference() {
    Fixture fixture = fixture();
    UUID resultId = UUID.randomUUID(), workOrderId = UUID.randomUUID();
    when(fixture.results().saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              OrderCoverResult result = invocation.getArgument(0);
              result.setId(resultId);
              return result;
            });
    when(fixture.production().requestDraftProductionOrder(any())).thenReturn(workOrderId);

    var decision =
        fixture
            .service()
            .confirm(
                TENANT,
                ORDER,
                UUID.randomUUID(),
                fixture.payload("Production is required by the customer"));

    assertThat(decision).isEqualTo(new OrderCoverCommandPort.Decision.Accepted(resultId, Set.of()));
    ArgumentCaptor<OrderCoverResult> receipt = ArgumentCaptor.forClass(OrderCoverResult.class);
    verify(fixture.results()).saveAndFlush(receipt.capture());
    assertThat(receipt.getValue().getRationale())
        .isEqualTo("Production is required by the customer");
    verify(fixture.lineResults())
        .save(
            org.mockito.ArgumentMatchers.argThat(
                line ->
                    line.getSuitabilityAtDecision() == OrderCoverEvidenceDto.Suitability.UNKNOWN
                        && line.getWorkOrderId().equals(workOrderId)
                        && "MAKE_TO_ORDER".equals(line.getOutcome())));
  }

  @Test
  void unknownSuitabilityWithoutRationaleIsAZeroWriteBusinessRejection() {
    Fixture fixture = fixture();

    var decision =
        fixture.service().confirm(TENANT, ORDER, UUID.randomUUID(), fixture.payload(null));

    assertThat(decision)
        .isInstanceOfSatisfying(
            OrderCoverCommandPort.Decision.Rejected.class,
            rejection -> assertThat(rejection.code()).isEqualTo("RATIONALE_REQUIRED"));
    verify(fixture.results(), never()).saveAndFlush(any());
    verify(fixture.production(), never()).requestDraftProductionOrder(any());
    verify(fixture.lineResults(), never()).save(any());
  }

  @Test
  void commandRejectsDuplicateLineSelectionBeforeSettlement() {
    UUID line = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                new ConfirmProductionCoverPayload(
                    UUID.randomUUID(), UUID.randomUUID(), 1, List.of(line, line), "reason", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unique");
  }

  private static Fixture fixture() {
    TenantContext.setCurrentTenantId(TENANT);
    UUID lineId = UUID.randomUUID(), evidenceId = UUID.randomUUID();
    OrderCoverCaseRepository cases = mock(OrderCoverCaseRepository.class);
    OrderCoverCaseLineRepository caseLines = mock(OrderCoverCaseLineRepository.class);
    OrderCoverEvidenceRepository evidence = mock(OrderCoverEvidenceRepository.class);
    SalesOrderRepository orders = mock(SalesOrderRepository.class);
    SalesOrderLineRepository lines = mock(SalesOrderLineRepository.class);
    OrderCoverEvidenceService evidenceService = mock(OrderCoverEvidenceService.class);
    OrderCoverResultRepository results = mock(OrderCoverResultRepository.class);
    OrderCoverLineResultRepository lineResults = mock(OrderCoverLineResultRepository.class);
    ProductionOrderPort production = mock(ProductionOrderPort.class);
    SalesOrderReservationPort reservations = mock(SalesOrderReservationPort.class);

    SalesOrder order = mock(SalesOrder.class);
    when(order.getCoverRegime()).thenReturn(OrderCoverRegime.GOVERNED);
    when(order.getStatus()).thenReturn(OrderStatus.CONFIRMED);
    when(order.getIsActive()).thenReturn(true);
    when(order.getTradingPartnerId()).thenReturn(UUID.randomUUID());
    OrderCoverCase coverCase = mock(OrderCoverCase.class);
    when(coverCase.getId()).thenReturn(CASE);
    when(coverCase.getSalesOrderId()).thenReturn(ORDER);
    when(coverCase.getState()).thenReturn(OrderCoverCaseState.OPEN);
    OrderCoverCaseLine scope = mock(OrderCoverCaseLine.class);
    when(scope.getSalesOrderLineId()).thenReturn(lineId);
    when(scope.unresolved()).thenReturn(true, false);
    RequirementProfileSnapshot profile = mock(RequirementProfileSnapshot.class);
    when(profile.complete()).thenReturn(true);
    when(profile.profileId()).thenReturn(UUID.randomUUID());
    when(profile.profileVersion()).thenReturn(1);
    when(profile.facets()).thenReturn(List.of());
    SalesOrderLine line = mock(SalesOrderLine.class);
    when(line.getId()).thenReturn(lineId);
    when(line.getRequestedQty()).thenReturn(BigDecimal.TEN);
    when(line.getShippedQty()).thenReturn(BigDecimal.ZERO);
    when(line.getLineStatus()).thenReturn(SalesOrderLineStatus.PENDING);
    when(line.getRequirementProfileSnapshot()).thenReturn(profile);
    when(line.getUnit()).thenReturn("kg");
    when(line.getCurrency()).thenReturn("GBP");
    OrderCoverEvidenceDto.Quantity requested =
        OrderCoverEvidenceDto.Quantity.known(BigDecimal.TEN, "kg");
    var lineEvidence =
        new OrderCoverEvidenceDto.Line(
            lineId,
            0,
            null,
            requested,
            requested,
            requested,
            OrderCoverEvidenceDto.Quantity.unknown("kg", "SUITABILITY_EVIDENCE_UNKNOWN"),
            OrderCoverEvidenceDto.Suitability.UNKNOWN,
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
    when(cases.lock(TENANT, CASE)).thenReturn(Optional.of(coverCase));
    when(caseLines.lockAll(TENANT, CASE)).thenReturn(List.of(scope));
    when(lines.lockAllForOrder(TENANT, ORDER)).thenReturn(List.of(line));
    when(evidence.findByTenantIdAndSalesOrderIdAndId(TENANT, ORDER, evidenceId))
        .thenReturn(Optional.of(snapshot));
    when(evidence.findFirstByTenantIdAndCaseIdOrderByRevisionDesc(TENANT, CASE))
        .thenReturn(Optional.of(snapshot));
    when(evidenceService.revalidate(ORDER, evidenceId))
        .thenReturn(new OrderCoverEvidenceService.Revalidation(true, "current"));
    var service =
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
            mock(DomainEventPublisher.class),
            Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneOffset.UTC),
            mock(SalesOrderLineFulfilmentLock.class));
    return new Fixture(service, results, lineResults, production, lineId, evidenceId);
  }

  private record Fixture(
      OrderCoverService service,
      OrderCoverResultRepository results,
      OrderCoverLineResultRepository lineResults,
      ProductionOrderPort production,
      UUID lineId,
      UUID evidenceId) {
    ConfirmProductionCoverPayload payload(String rationale) {
      return new ConfirmProductionCoverPayload(
          CASE, evidenceId, 1, List.of(lineId), rationale, null);
    }
  }
}
