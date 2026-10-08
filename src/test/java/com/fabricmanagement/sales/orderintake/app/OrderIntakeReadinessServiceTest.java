package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.production.core.stockunit.api.PieceAllocationPort;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeAction;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeReadinessDto;
import com.fabricmanagement.sales.orderintake.infra.repository.QuantityAcceptanceRepository;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderIntakeReadinessServiceTest {

  @Mock private OrderIntakeAccess access;
  @Mock private SalesOrderLineRepository lines;
  @Mock private QuantityAcceptanceRepository acceptances;
  @Mock private ConfirmationGate gate;
  @Mock private PieceAllocationPort allocation;
  @Mock private IntakePermissions permissions;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private SalesOrder order;
  private OrderIntakeReadinessService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    order =
        SalesOrder.builder()
            .tradingPartnerId(UUID.randomUUID())
            .orderNumber("SO-1")
            .status(OrderStatus.DRAFT)
            .orderDate(LocalDate.of(2026, 9, 30))
            .build();
    ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
    when(access.readableOrder(any(), any())).thenReturn(order);
    when(access.canWrite(order, actor)).thenReturn(true);
    when(permissions.has(any(PermissionKey.class))).thenReturn(true);
    when(lines.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(any())).thenReturn(List.of());
    when(gate.blocks(any(), any())).thenReturn(List.of());
    when(allocation.activeForOrder(any(), any())).thenReturn(List.of());
    service =
        new OrderIntakeReadinessService(
            access,
            lines,
            acceptances,
            gate,
            allocation,
            permissions,
            Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void anOrderWithPlanningClosesOnlyTheActionsThatChangeItsContent() {
    ReflectionTestUtils.setField(order, "flowStage", OrderFlowStage.IN_PLANNING);

    Map<OrderIntakeAction, String> reasons = reasons();

    assertThat(reasons)
        .containsEntry(OrderIntakeAction.RECORD_STOCK_CHOICE, "ORDER_WITH_PLANNING")
        .containsEntry(OrderIntakeAction.RECORD_TONE_ACCEPTANCE, "ORDER_WITH_PLANNING")
        .containsEntry(OrderIntakeAction.ADD_CUSTOM_REQUEST, "ORDER_WITH_PLANNING")
        .containsEntry(OrderIntakeAction.RECORD_CUSTOMER_DECISION, "ORDER_WITH_PLANNING")
        .containsEntry(OrderIntakeAction.RESOLVE_CUSTOM_REQUEST, "ORDER_WITH_PLANNING")
        .containsEntry(OrderIntakeAction.CORRECT_PRODUCT, "ORDER_WITH_PLANNING")
        .containsEntry(OrderIntakeAction.EVALUATE_QUANTITY, "")
        .containsEntry(OrderIntakeAction.UPLOAD_ATTACHMENT, "")
        .containsEntry(OrderIntakeAction.REQUEST_READINESS_CONFIRMATION, "")
        .containsEntry(OrderIntakeAction.REQUEST_HOLD, "");
  }

  @Test
  void aDraftOrderKeepsItsContentActionsOpen() {
    assertThat(reasons())
        .containsEntry(OrderIntakeAction.RECORD_STOCK_CHOICE, "")
        .containsEntry(OrderIntakeAction.CORRECT_PRODUCT, "");
  }

  private Map<OrderIntakeAction, String> reasons() {
    OrderIntakeReadinessDto readiness = service.readiness(order.getId(), actor);
    return readiness.capabilities().stream()
        .collect(
            Collectors.toMap(
                OrderIntakeReadinessDto.Capability::action,
                capability -> capability.reason() == null ? "" : capability.reason()));
  }
}
