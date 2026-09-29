package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.OrderTotals;
import com.fabricmanagement.production.core.workorder.api.query.ProductionHistoryQueryService;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.domain.CoverPortionKind;
import com.fabricmanagement.sales.orderintake.domain.LineGreigeCover;
import com.fabricmanagement.sales.orderintake.domain.LinePortionReadiness;
import com.fabricmanagement.sales.orderintake.domain.PartialDeliveryPreference;
import com.fabricmanagement.sales.orderintake.dto.FulfilmentDtos;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerProductRequestRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.LineGreigeCoverRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.LinePortionReadinessRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.OrderArrivalEstimateRepository;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.port.LineStockPortionPort;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** SOI D5/D6: portions add up to the open quantity; dates are confirmed, never assumed. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoverPlanningServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ACTOR = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

  @Mock private OrderIntakeAccess access;
  @Mock private SalesOrderRepository orders;
  @Mock private SalesOrderLineRepository lines;
  @Mock private LineGreigeCoverRepository greigeCovers;
  @Mock private LinePortionReadinessRepository readiness;
  @Mock private OrderArrivalEstimateRepository arrivals;
  @Mock private ProductionHistoryQueryService history;
  @Mock private DeliveryPreferenceService deliveryPreference;
  @Mock private CustomerProductRequestRepository customRequests;

  private CoverPlanningService service;
  private SalesOrder order;
  private SalesOrderLine line;
  private Optional<BigDecimal> heldStock = Optional.of(new BigDecimal("212"));

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    LineStockPortionPort stockPortion = ignored -> heldStock;
    service =
        new CoverPlanningService(
            access,
            orders,
            lines,
            greigeCovers,
            readiness,
            arrivals,
            stockPortion,
            history,
            deliveryPreference,
            customRequests,
            Clock.fixed(NOW, ZoneOffset.UTC));
    order =
        SalesOrder.builder()
            .totals(OrderTotals.zero("EUR"))
            .tradingPartnerId(UUID.randomUUID())
            .orderNumber("SO-6")
            .build();
    order.setId(UUID.randomUUID());
    line =
        SalesOrderLine.builder()
            .salesOrderId(order.getId())
            .productId(UUID.randomUUID())
            .requestedQty(new BigDecimal("500"))
            .unit("M")
            .build();
    line.setId(UUID.randomUUID());
    when(lines.findByTenantIdAndId(TENANT, line.getId())).thenReturn(Optional.of(line));
    when(orders.findByTenantIdAndId(TENANT, order.getId())).thenReturn(Optional.of(order));
    when(access.readableOrder(order.getId(), ACTOR)).thenReturn(order);
    when(lines.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(order.getId()))
        .thenReturn(List.of(line));
    when(history.yieldHistory(any(), any()))
        .thenReturn(new ProductionHistoryQueryService.YieldHistory(0, null, null, null, null));
    when(history.leadTimeHistory(any(), any()))
        .thenReturn(new ProductionHistoryQueryService.LeadTimeHistory(0, null, null, null, null));
    when(greigeCovers.findFirstByTenantIdAndSalesOrderLineIdAndStatus(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(readiness.findByTenantIdAndSalesOrderLineIdInAndStatusIn(any(), any(), any()))
        .thenReturn(List.of());
    when(deliveryPreference.preferenceOf(order.getId()))
        .thenReturn(PartialDeliveryPreference.UNKNOWN);
    when(arrivals.findFirstByTenantIdAndSalesOrderIdAndSupersededAtIsNull(TENANT, order.getId()))
        .thenReturn(Optional.empty());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("S06: 212 m held + 288 m new supply; no date until planning and warehouse confirm")
  void portionsAddUpAndNoDateIsAssumed() {
    FulfilmentDtos.DeliveryOutlook outlook = service.outlook(order.getId(), ACTOR);

    FulfilmentDtos.LineOutlook view = outlook.lines().getFirst();
    Map<CoverPortionKind, FulfilmentDtos.PortionOutlook> portions = byKind(view);
    assertThat(portions.get(CoverPortionKind.FINISHED_STOCK).quantity())
        .isEqualByComparingTo("212");
    assertThat(portions.get(CoverPortionKind.NEW_SUPPLY).quantity()).isEqualByComparingTo("288");
    assertThat(portions).doesNotContainKey(CoverPortionKind.FROM_GREIGE);
    assertThat(view.readyOn()).isNull();
    assertThat(view.history().sampleSize()).isZero();
    assertThat(outlook.arrival()).as("A07-b: no source, no arrival").isNull();
    assertThat(outlook.readyPartsMayShipFirst()).as("A10: UNKNOWN is not permission").isFalse();
  }

  @Test
  @DisplayName("S07: 150 m from greige leaves 138 m new supply")
  void greigePortionReducesNewSupply() {
    LineGreigeCover cover =
        LineGreigeCover.confirm(
            order.getId(),
            line.getId(),
            new BigDecimal("150"),
            "M",
            List.of(),
            "HAM-77, recipe v2",
            0,
            null,
            ACTOR,
            NOW);
    when(greigeCovers.findFirstByTenantIdAndSalesOrderLineIdAndStatus(
            TENANT, line.getId(), LineGreigeCover.Status.ACTIVE))
        .thenReturn(Optional.of(cover));

    Map<CoverPortionKind, FulfilmentDtos.PortionOutlook> portions =
        byKind(service.outlook(order.getId(), ACTOR).lines().getFirst());

    assertThat(portions.get(CoverPortionKind.FROM_GREIGE).quantity()).isEqualByComparingTo("150");
    assertThat(portions.get(CoverPortionKind.FROM_GREIGE).quantityConfirmed()).isTrue();
    assertThat(portions.get(CoverPortionKind.NEW_SUPPLY).quantity()).isEqualByComparingTo("138");
  }

  @Test
  @DisplayName("R12: greige cannot cover more than the open quantity less held stock")
  void greigeCannotExceedTheRest() {
    assertThatThrownBy(
            () ->
                service.confirmGreigeCover(
                    line.getId(),
                    new FulfilmentDtos.ConfirmGreigeCover(
                        new BigDecimal("300"), List.of(), "HAM-77"),
                    ACTOR))
        .isInstanceOf(OrderIntakeException.class)
        .extracting(error -> ((OrderIntakeException) error).getErrorCode())
        .isEqualTo("ORDER_INTAKE_GREIGE_EXCEEDS_OPEN");
    verify(greigeCovers, never()).save(any());
  }

  @Test
  @DisplayName("R15: the line date is the latest confirmed portion date, once all are confirmed")
  void lineDateIsTheLatestPortion() {
    LinePortionReadiness stock =
        LinePortionReadiness.unrequested(
            order.getId(), line.getId(), CoverPortionKind.FINISHED_STOCK);
    stock.confirm(LocalDate.parse("2026-10-02"), "picked and packed", ACTOR, NOW);
    LinePortionReadiness supply =
        LinePortionReadiness.unrequested(order.getId(), line.getId(), CoverPortionKind.NEW_SUPPLY);
    when(readiness.findByTenantIdAndSalesOrderLineIdInAndStatusIn(any(), any(), any()))
        .thenReturn(List.of(stock, supply));

    assertThat(service.outlook(order.getId(), ACTOR).lines().getFirst().readyOn()).isNull();

    supply.confirm(LocalDate.parse("2026-10-20"), "dye plan week 42", ACTOR, NOW);
    assertThat(service.outlook(order.getId(), ACTOR).lines().getFirst().readyOn())
        .isEqualTo(LocalDate.parse("2026-10-20"));
  }

  @Test
  @DisplayName("S09: held stock that cannot be stated keeps the new-supply portion unknown")
  void unknownStockKeepsSupplyUnknown() {
    heldStock = Optional.empty();
    Map<CoverPortionKind, FulfilmentDtos.PortionOutlook> portions =
        byKind(service.outlook(order.getId(), ACTOR).lines().getFirst());
    assertThat(portions.get(CoverPortionKind.FINISHED_STOCK).quantity()).isNull();
    assertThat(portions.get(CoverPortionKind.FINISHED_STOCK).quantityConfirmed()).isFalse();
    assertThat(portions.get(CoverPortionKind.NEW_SUPPLY).quantity()).isNull();
  }

  @Test
  void existingOverCoverIsVisibleAndCannotReceiveAReadinessPromise() {
    LineGreigeCover cover =
        LineGreigeCover.confirm(
            order.getId(),
            line.getId(),
            new BigDecimal("400"),
            "M",
            List.of(),
            "Existing plan",
            0,
            null,
            ACTOR,
            NOW);
    when(greigeCovers.findFirstByTenantIdAndSalesOrderLineIdAndStatus(
            TENANT, line.getId(), LineGreigeCover.Status.ACTIVE))
        .thenReturn(Optional.of(cover));
    var view = service.outlook(order.getId(), ACTOR).lines().getFirst();
    assertThat(view.blockingReason()).isEqualTo("COVER_EXCEEDS_OPEN");
    assertThat(view.readyOn()).isNull();
    assertThat(byKind(view).get(CoverPortionKind.FROM_GREIGE).quantity())
        .isEqualByComparingTo("400");
    assertThatThrownBy(
            () ->
                service.confirmProductionReadiness(
                    line.getId(),
                    CoverPortionKind.FROM_GREIGE,
                    new FulfilmentDtos.ConfirmReadiness(LocalDate.parse("2026-10-20"), "plan"),
                    ACTOR))
        .isInstanceOf(OrderIntakeException.class);
    verify(readiness, never()).save(any());
  }

  @Test
  void unknownStockCannotBeConfirmedReadyToShip() {
    heldStock = Optional.empty();
    assertThatThrownBy(
            () ->
                service.confirmShipReadiness(
                    line.getId(),
                    new FulfilmentDtos.ConfirmReadiness(LocalDate.parse("2026-10-20"), "packed"),
                    ACTOR))
        .isInstanceOf(OrderIntakeException.class);
    verify(readiness, never()).save(any());
  }

  private static Map<CoverPortionKind, FulfilmentDtos.PortionOutlook> byKind(
      FulfilmentDtos.LineOutlook view) {
    return view.portions().stream()
        .collect(Collectors.toMap(FulfilmentDtos.PortionOutlook::portion, Function.identity()));
  }
}
