package com.fabricmanagement.production.execution.workorder.app.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.workorder.app.WorkOrderCostRecalculationService;
import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderCompletedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkOrderCostBridgeListener")
class WorkOrderCostBridgeListenerTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID WORK_ORDER_ID = UUID.randomUUID();

  @Mock private WorkOrderCostRecalculationService costRecalculationService;
  @Mock private IdempotentEventHandler idempotentHandler;

  @InjectMocks private WorkOrderCostBridgeListener listener;

  @BeforeEach
  void setUp() {
    doAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(3)).run();
              return null;
            })
        .when(idempotentHandler)
        .executeOnce(any(), any(), any(), any());
    TenantContext.clear();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("sets TenantContext from event before recalculating actual cost")
  void setsTenantContextFromEventBeforeRecalculatingActualCost() {
    doAnswer(
            invocation -> {
              assertThat(TenantContext.requireTenantId()).isEqualTo(TENANT_ID);
              return null;
            })
        .when(costRecalculationService)
        .recalculateActualCost(WORK_ORDER_ID);

    WorkOrderCompletedEvent event = buildEvent();

    assertThatCode(() -> listener.handleWorkOrderCompletedEvent(event)).doesNotThrowAnyException();

    verify(costRecalculationService).recalculateActualCost(WORK_ORDER_ID);
    assertThat(TenantContext.getCurrentTenantIdOrNull()).isNull();
  }

  @Test
  @DisplayName("swallows recalculation failures")
  void swallowsRecalculationFailures() {
    doThrow(new RuntimeException("missing price list"))
        .when(costRecalculationService)
        .recalculateActualCost(WORK_ORDER_ID);

    WorkOrderCompletedEvent event = buildEvent();

    assertThatCode(() -> listener.handleWorkOrderCompletedEvent(event)).doesNotThrowAnyException();

    verify(costRecalculationService).recalculateActualCost(WORK_ORDER_ID);
  }

  private WorkOrderCompletedEvent buildEvent() {
    return new WorkOrderCompletedEvent(
        TENANT_ID,
        WORK_ORDER_ID,
        UUID.randomUUID(),
        "WO-TEST-002",
        new BigDecimal("1000.000"),
        new BigDecimal("985.000"),
        new BigDecimal("1010.000"),
        new BigDecimal("97.50"),
        Instant.now(),
        UUID.randomUUID());
  }
}
