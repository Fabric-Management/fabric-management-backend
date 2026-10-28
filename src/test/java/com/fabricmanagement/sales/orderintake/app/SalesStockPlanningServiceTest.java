package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.batch.api.BatchLotQuantityIntentPort;
import com.fabricmanagement.production.core.stockunit.api.*;
import com.fabricmanagement.sales.common.domain.SalesQuantityPolicy;
import com.fabricmanagement.sales.orderintake.dto.SalesStockPlanningDto;
import com.fabricmanagement.sales.quote.app.QuoteAccessPolicy;
import com.fabricmanagement.sales.quote.infra.repository.QuoteRepository;
import com.fabricmanagement.sales.salesorder.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;

class SalesStockPlanningServiceTest {
  final UUID tenant = UUID.randomUUID(),
      actor = UUID.randomUUID(),
      orderId = UUID.randomUUID(),
      lineId = UUID.randomUUID();
  final OrderIntakeAccess access = mock(OrderIntakeAccess.class);
  final StockUnitSoftHoldPort holds = mock(StockUnitSoftHoldPort.class);
  final BatchLotQuantityIntentPort intents = mock(BatchLotQuantityIntentPort.class);
  final PieceAllocationPort allocations = mock(PieceAllocationPort.class);
  final SalesStockPlanningService service =
      new SalesStockPlanningService(
          access,
          mock(QuoteRepository.class),
          mock(QuoteAccessPolicy.class),
          holds,
          intents,
          allocations,
          mock(StockUnitCutPlanPort.class));
  SalesOrder order;
  SalesOrderLine line;

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    order = SalesOrder.builder().orderNumber("SO-2026-00125").build();
    order.setId(orderId);
    line =
        SalesOrderLine.builder()
            .productId(UUID.randomUUID())
            .requestedQty(new BigDecimal("2050"))
            .customerRequestQty(new BigDecimal("2050"))
            .unit("M")
            .build();
    line.setId(lineId);
    when(access.readableOrder(orderId, actor)).thenReturn(order);
    when(access.line(order, lineId)).thenReturn(line);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void metreIntentIsNotDescribedAsPhysicalRollReservation() {
    when(intents.hasActiveIntents(lineId)).thenReturn(true);
    var result = service.order(orderId, lineId, actor);
    assertThat(result.assignment()).isEqualTo(SalesStockPlanningDto.Assignment.LOT_QUANTITY_INTENT);
    assertThat(result.heldPieceIds()).isEmpty();
    assertThat(result.allocatedPieceIds()).isEmpty();
    assertThat(result.review()).isEqualTo(SalesStockPlanningDto.Review.PLANNING_REQUIRED);
  }

  @Test
  void confirmedAllocationRemainsVisibleAfterItsSoftHoldsHaveBeenConsumed() {
    UUID roll = UUID.randomUUID();
    when(allocations.activeForLine(tenant, lineId))
        .thenReturn(
            List.of(
                new PieceAllocationPort.PieceAllocationView(
                    UUID.randomUUID(),
                    roll,
                    UUID.randomUUID(),
                    lineId,
                    new BigDecimal("2050"),
                    "M",
                    Instant.now())));
    var result = service.order(orderId, lineId, actor);
    assertThat(result.assignment()).isEqualTo(SalesStockPlanningDto.Assignment.ROLLS_ALLOCATED);
    assertThat(result.heldPieceIds()).isEmpty();
    assertThat(result.allocatedPieceIds()).containsExactly(roll);
  }

  @Test
  void exactUnassignedNeedOffersNoCutExecutionAndNoWriteForAReadOnlyViewer() {
    line.recordQuantityPolicy(
        SalesQuantityPolicy.EXACT, actor, Instant.now(), "Customer requests exactly 2,050 metres");
    var result = service.order(orderId, lineId, actor);
    assertThat(result.cutAssessment())
        .isEqualTo(SalesStockPlanningDto.CutAssessment.EXACT_QUANTITY_REVIEW_REQUIRED);
    assertThat(result.capabilities()).allMatch(capability -> !capability.allowed());
    assertThat(result.capabilities())
        .anyMatch(
            capability ->
                capability.action() == SalesStockPlanningDto.Action.APPLY_CUT_PLAN
                    && capability.reason() == SalesStockPlanningDto.Reason.WRITE_ACCESS_REQUIRED);
    assertThat(result.commercialQuantity()).isEqualByComparingTo("2050");
  }

  @Test
  void historicalMissingRequestRemainsUnknown() {
    line.inheritQuantityTerms(null, null);
    var result = service.order(orderId, lineId, actor);
    assertThat(result.customerRequestQuantity()).isNull();
    assertThat(result.quantityTerms().policy()).isEqualTo(SalesQuantityPolicy.UNRECORDED);
  }
}
