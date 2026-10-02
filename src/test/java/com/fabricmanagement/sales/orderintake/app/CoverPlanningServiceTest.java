package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.production.core.workorder.api.query.ProductionHistoryQueryService;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
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
import com.fabricmanagement.sales.salesorder.app.WorkFixture;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkKind;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

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

  private WorkFixture work;
  private CoverPlanningService service;
  private SalesOrder order;
  private SalesOrderLine line;
  private Optional<BigDecimal> heldStock = Optional.of(new BigDecimal("212"));

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    LineStockPortionPort stockPortion = ignored -> heldStock;
    work = new WorkFixture(Clock.fixed(NOW, ZoneOffset.UTC));
    work.user(
        ACTOR,
        "PLANNING",
        Map.of(
            PermissionKey.PRODUCTION_READ, DataScope.OWN,
            PermissionKey.PRODUCTION_WRITE, DataScope.OWN,
            PermissionKey.PRODUCTION_CLAIM, DataScope.DEPARTMENT,
            PermissionKey.LOGISTICS_READ, DataScope.OWN,
            PermissionKey.LOGISTICS_PREPARE, DataScope.OWN,
            PermissionKey.LOGISTICS_CLAIM, DataScope.DEPARTMENT));
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
            work.service,
            Clock.fixed(NOW, ZoneOffset.UTC));
    order =
        SalesOrder.builder()
            .tradingPartnerId(UUID.randomUUID())
            .orderNumber("SO-6")
            .status(OrderStatus.DRAFT)
            .build();
    order.setId(UUID.randomUUID());
    // The order is with planning and ACTOR took it.
    ReflectionTestUtils.setField(order, "flowStage", OrderFlowStage.IN_PLANNING);
    work.order(order);
    work.service.route(order.getId(), OrderWorkKind.PLANNING, null);
    work.service.claim(order.getId(), OrderWorkKind.PLANNING, ACTOR);
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
    UUID warehouse = warehouseMember();
    work.service.route(order.getId(), OrderWorkKind.SHIP_READINESS, null);
    work.service.claim(order.getId(), OrderWorkKind.SHIP_READINESS, warehouse);
    assertThatThrownBy(
            () ->
                service.confirmShipReadiness(
                    line.getId(),
                    new FulfilmentDtos.ConfirmReadiness(LocalDate.parse("2026-10-20"), "packed"),
                    warehouse))
        .isInstanceOf(OrderIntakeException.class);
    verify(readiness, never()).save(any());
  }

  @Test
  void planningInputOutsideTheEvaluationIsRefused() {
    ReflectionTestUtils.setField(order, "flowStage", OrderFlowStage.PLANNED);
    LineGreigeCover cover =
        LineGreigeCover.confirm(
            order.getId(),
            line.getId(),
            new BigDecimal("50"),
            "M",
            List.of(),
            "HAM-1",
            0,
            null,
            ACTOR,
            NOW);
    when(greigeCovers.findFirstByTenantIdAndSalesOrderLineIdAndStatus(
            TENANT, line.getId(), LineGreigeCover.Status.ACTIVE))
        .thenReturn(Optional.of(cover));

    assertThatThrownBy(() -> service.withdrawGreigeCover(line.getId(), ACTOR))
        .isInstanceOf(OrderDomainException.class)
        .extracting(error -> ((OrderDomainException) error).getErrorCode())
        .isEqualTo("EVALUATION_CLOSED");
    ReflectionTestUtils.setField(order, "flowStage", OrderFlowStage.AWAITING_PLANNING);
    assertThatThrownBy(() -> service.withdrawGreigeCover(line.getId(), ACTOR))
        .isInstanceOf(OrderDomainException.class)
        .extracting(error -> ((OrderDomainException) error).getErrorCode())
        .isEqualTo("EVALUATION_NOT_STARTED");
    ReflectionTestUtils.setField(order, "status", OrderStatus.CANCELLED);
    assertThatThrownBy(() -> service.withdrawGreigeCover(line.getId(), ACTOR))
        .isInstanceOf(OrderDomainException.class)
        .extracting(error -> ((OrderDomainException) error).getErrorCode())
        .isEqualTo("ORDER_CLOSED");
    assertThat(cover.getStatus()).isEqualTo(LineGreigeCover.Status.ACTIVE);
    verify(greigeCovers, never()).save(any());
  }

  @Test
  void aPlannerWithoutScopeOverTheOrderCannotWithdrawItsCover() {
    UUID colleague =
        work.user(
            "PLANNING",
            Map.of(
                PermissionKey.PRODUCTION_READ, DataScope.OWN,
                PermissionKey.PRODUCTION_WRITE, DataScope.OWN,
                PermissionKey.PRODUCTION_CLAIM, DataScope.DEPARTMENT));

    assertThatThrownBy(() -> service.withdrawGreigeCover(line.getId(), colleague))
        .isInstanceOf(
            com.fabricmanagement.common.infrastructure.web.exception.NotFoundException.class);
    verify(greigeCovers, never()).save(any());
  }

  @Test
  void theArrivalEstimateNeedsShippingResponsibilityAndAnOrderInFulfilment() {
    UUID shipper =
        work.user(
            "SHIPPING",
            Map.of(
                PermissionKey.LOGISTICS_READ, DataScope.OWN,
                PermissionKey.LOGISTICS_WRITE, DataScope.OWN,
                PermissionKey.LOGISTICS_CLAIM, DataScope.DEPARTMENT));
    var estimate =
        new FulfilmentDtos.RecordArrivalEstimate(
            LocalDate.parse("2026-10-22"),
            LocalDate.parse("2026-10-24"),
            com.fabricmanagement.sales.orderintake.domain.OrderArrivalEstimate.Source.CARRIER,
            "TRK-1");

    // Not routed: the order is not in fulfilment yet.
    assertThatThrownBy(() -> service.recordArrival(order.getId(), estimate, shipper))
        .isInstanceOf(OrderDomainException.class)
        .extracting(error -> ((OrderDomainException) error).getErrorCode())
        .isEqualTo("WORK_NOT_ROUTED");
    work.service.route(order.getId(), OrderWorkKind.ARRIVAL_ESTIMATE, null);
    assertThatThrownBy(() -> service.recordArrival(order.getId(), estimate, shipper))
        .isInstanceOf(OrderDomainException.class)
        .extracting(error -> ((OrderDomainException) error).getErrorCode())
        .isEqualTo("WORK_NOT_CLAIMED");
    work.service.claim(order.getId(), OrderWorkKind.ARRIVAL_ESTIMATE, shipper);
    assertThatThrownBy(() -> service.recordArrival(order.getId(), estimate, shipper))
        .isInstanceOf(OrderDomainException.class)
        .extracting(error -> ((OrderDomainException) error).getErrorCode())
        .isEqualTo("ORDER_NOT_IN_PROCESSING");
    verify(arrivals, never()).save(any());
  }

  @Test
  void readOnlyLogisticsCannotConfirmShipReadiness() {
    UUID reader =
        work.user("WAREHOUSE", Map.of(PermissionKey.LOGISTICS_READ, DataScope.ORGANIZATION));
    work.service.route(order.getId(), OrderWorkKind.SHIP_READINESS, null);
    work.service.claim(order.getId(), OrderWorkKind.SHIP_READINESS, warehouseMember());

    assertThatThrownBy(
            () ->
                service.confirmShipReadiness(
                    line.getId(),
                    new FulfilmentDtos.ConfirmReadiness(LocalDate.parse("2026-10-20"), "packed"),
                    reader))
        .isInstanceOf(AccessDeniedException.class);
    verify(readiness, never()).save(any());
  }

  @Test
  void theWarehouseQueueShowsOnlyRequestsInTheUsersScope() {
    LinePortionReadiness request =
        LinePortionReadiness.request(
            order.getId(), line.getId(), CoverPortionKind.FINISHED_STOCK, ACTOR, NOW);
    when(readiness.findByTenantIdAndStatusAndPortionInOrderByRequestedAtAscIdAsc(
            any(), any(), any()))
        .thenReturn(List.of(request));
    when(orders.findAllById(any())).thenReturn(List.of(order));
    ReflectionTestUtils.setField(order, "tenantId", TENANT);
    UUID warehouseWorker =
        work.user(
            "WAREHOUSE",
            Map.of(
                PermissionKey.LOGISTICS_READ, DataScope.OWN,
                PermissionKey.LOGISTICS_PREPARE, DataScope.OWN,
                PermissionKey.LOGISTICS_CLAIM, DataScope.DEPARTMENT));
    UUID shipping =
        work.user("SHIPPING", Map.of(PermissionKey.LOGISTICS_CLAIM, DataScope.DEPARTMENT));
    work.service.route(order.getId(), OrderWorkKind.SHIP_READINESS, null);

    assertThat(service.openRequests(true, shipping)).isEmpty();
    var visible = service.openRequests(true, warehouseWorker);
    assertThat(visible).hasSize(1);
    assertThat(visible.getFirst().actions())
        .anySatisfy(
            capability -> {
              assertThat(capability.action().name()).isEqualTo("CLAIM");
              assertThat(capability.allowed()).isTrue();
            })
        .anySatisfy(
            capability -> {
              assertThat(capability.action().name()).isEqualTo("CONFIRM_READINESS");
              assertThat(capability.reason()).isEqualTo("WORK_NOT_CLAIMED");
            });
  }

  private UUID warehouseMember() {
    return work.user(
        "WAREHOUSE",
        Map.of(
            PermissionKey.LOGISTICS_READ, DataScope.OWN,
            PermissionKey.LOGISTICS_PREPARE, DataScope.OWN,
            PermissionKey.LOGISTICS_CLAIM, DataScope.DEPARTMENT));
  }

  private static Map<CoverPortionKind, FulfilmentDtos.PortionOutlook> byKind(
      FulfilmentDtos.LineOutlook view) {
    return view.portions().stream()
        .collect(Collectors.toMap(FulfilmentDtos.PortionOutlook::portion, Function.identity()));
  }
}
