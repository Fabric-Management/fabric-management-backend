package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.OrderTotals;
import com.fabricmanagement.production.core.batch.api.LotCompatibilityRequestPort;
import com.fabricmanagement.production.core.workorder.api.WorkOrderHoldPort;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.domain.LineProductCorrection;
import com.fabricmanagement.sales.orderintake.dto.FulfilmentDtos;
import com.fabricmanagement.sales.orderintake.infra.repository.LineGreigeCoverRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.LinePortionReadinessRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.LineProductCorrectionRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.QuantityAcceptanceRepository;
import com.fabricmanagement.sales.salesorder.app.CatalogLineValidator;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.port.ProductionOrderPort;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** SOI S12 / R19: traced product correction. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductCorrectionServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ACTOR = UUID.randomUUID();
  private static final UUID OLD = UUID.randomUUID();
  private static final UUID NEW = UUID.randomUUID();

  @Mock private OrderIntakeAccess access;
  @Mock private SalesOrderLineRepository lines;
  @Mock private LineProductCorrectionRepository corrections;
  @Mock private LineGreigeCoverRepository greigeCovers;
  @Mock private QuantityAcceptanceRepository acceptances;
  @Mock private LinePortionReadinessRepository readiness;
  @Mock private CatalogLineValidator validator;
  @Mock private ProductionOrderPort production;
  @Mock private WorkOrderHoldPort holds;

  @Mock
  private com.fabricmanagement.common.infrastructure.persistence.SalesOrderLineFulfilmentLock
      fulfilmentLock;

  @Mock private ConfirmationGate gate;
  @Mock private LotCompatibilityRequestPort compatibilityRequests;

  private ProductCorrectionService service;
  private SalesOrder order;
  private SalesOrderLine navy160;
  private SalesOrderLine navy155;
  private SalesOrderLine other;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    service =
        new ProductCorrectionService(
            access,
            lines,
            corrections,
            greigeCovers,
            acceptances,
            readiness,
            validator,
            production,
            holds,
            gate,
            compatibilityRequests,
            Clock.fixed(Instant.parse("2026-09-27T10:00:00Z"), ZoneOffset.UTC),
            fulfilmentLock);
    order =
        SalesOrder.builder()
            .totals(OrderTotals.zero("EUR"))
            .tradingPartnerId(UUID.randomUUID())
            .orderNumber("SO-5")
            .build();
    order.setId(UUID.randomUUID());
    navy160 = line(OLD, 2L);
    navy155 = line(OLD, 5L);
    other = line(UUID.randomUUID(), 1L);
    when(access.writableOrder(order.getId(), ACTOR)).thenReturn(order);
    when(lines.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(order.getId()))
        .thenReturn(List.of(navy160, navy155, other));
    when(greigeCovers.findFirstByTenantIdAndSalesOrderLineIdAndStatus(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(corrections.findByTenantIdAndSalesOrderIdOrderByCorrectedAtDescIdDesc(
            TENANT, order.getId()))
        .thenReturn(List.of());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("S12: every distribution of the product is corrected, traced and re-checked")
  void correctsTheWholeGroup() {
    service.correct(order.getId(), request(List.of(ref(navy160, 2L), ref(navy155, 5L))), ACTOR);

    assertThat(navy160.getProductId()).isEqualTo(NEW);
    assertThat(navy155.getProductId()).isEqualTo(NEW);
    assertThat(other.getProductId()).isNotEqualTo(NEW);
    assertThat(navy160.getRequirementProfileSnapshot()).isNull();
    verify(corrections, org.mockito.Mockito.times(2)).save(any(LineProductCorrection.class));
    verify(validator).validate(eq(TENANT), eq(order.getTradingPartnerId()), any());
    verify(gate).release(any(), eq(ACTOR), eq(ProductCorrectionService.RELEASE_REASON));
  }

  @Test
  @DisplayName("IK-03: a partial group is refused")
  void partialGroupRefused() {
    assertThatThrownBy(
            () -> service.correct(order.getId(), request(List.of(ref(navy160, 2L))), ACTOR))
        .isInstanceOf(OrderIntakeException.class)
        .extracting(error -> ((OrderIntakeException) error).getErrorCode())
        .isEqualTo("ORDER_INTAKE_PRODUCT_GROUP_MISMATCH");
  }

  @Test
  @DisplayName("R19: a stale version is refused")
  void staleVersion() {
    assertThatThrownBy(
            () ->
                service.correct(
                    order.getId(), request(List.of(ref(navy160, 1L), ref(navy155, 5L))), ACTOR))
        .isInstanceOf(OrderIntakeException.class)
        .extracting(error -> ((OrderIntakeException) error).getErrorCode())
        .isEqualTo("ORDER_INTAKE_STALE_VERSION");
    verify(corrections, never()).save(any());
  }

  @Test
  @DisplayName("S12: running work needs a confirmed hold first")
  void runningWorkNeedsHold() {
    when(production.hasActiveProduction(TENANT, navy155.getId())).thenReturn(true);
    when(holds.isStoppedFor(TENANT, navy155.getId())).thenReturn(false);

    assertThatThrownBy(
            () ->
                service.correct(
                    order.getId(), request(List.of(ref(navy160, 2L), ref(navy155, 5L))), ACTOR))
        .isInstanceOf(OrderIntakeException.class)
        .extracting(error -> ((OrderIntakeException) error).getErrorCode())
        .isEqualTo("ORDER_INTAKE_HOLD_REQUIRED");

    when(holds.isStoppedFor(TENANT, navy155.getId())).thenReturn(true);
    service.correct(order.getId(), request(List.of(ref(navy160, 2L), ref(navy155, 5L))), ACTOR);
    assertThat(navy155.getProductId()).isEqualTo(NEW);
  }

  private FulfilmentDtos.CorrectProduct request(List<FulfilmentDtos.LineVersion> refs) {
    return new FulfilmentDtos.CorrectProduct(OLD, NEW, refs, null);
  }

  private static FulfilmentDtos.LineVersion ref(SalesOrderLine line, long version) {
    return new FulfilmentDtos.LineVersion(line.getId(), version);
  }

  private SalesOrderLine line(UUID product, long version) {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .salesOrderId(order == null ? null : order.getId())
            .productId(product)
            .requestedQty(new BigDecimal("100"))
            .unit("M")
            .build();
    line.setId(UUID.randomUUID());
    line.setVersion(version);
    return line;
  }
}
