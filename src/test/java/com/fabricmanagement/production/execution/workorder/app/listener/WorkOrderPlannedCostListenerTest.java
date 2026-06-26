package com.fabricmanagement.production.execution.workorder.app.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.workorder.app.WorkOrderPlannedCostTriggerService;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderApprovedEvent;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkOrderPlannedCostListener")
class WorkOrderPlannedCostListenerTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID WORK_ORDER_ID = UUID.randomUUID();

  @Mock private WorkOrderPlannedCostTriggerService plannedCostTriggerService;

  @InjectMocks private WorkOrderPlannedCostListener listener;

  @Mock
  private com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler
      idempotentHandler;

  @BeforeEach
  void setUp() {
    if (idempotentHandler != null) {
      org.mockito.Mockito.lenient()
          .doAnswer(
              invocation -> {
                ((Runnable) invocation.getArgument(3)).run();
                return null;
              })
          .when(idempotentHandler)
          .executeOnce(any(), any(), any(), any());
    }
    TenantContext.clear();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private WorkOrderApprovedEvent buildEvent() {
    return new WorkOrderApprovedEvent(
        TENANT_ID,
        WORK_ORDER_ID,
        "WO-TEST-001",
        WorkOrderModuleType.SPINNING,
        UUID.randomUUID(),
        new BigDecimal("1000.000"),
        UUID.randomUUID(),
        UUID.randomUUID());
  }

  @Nested
  @DisplayName("handleWorkOrderApprovedEvent() — resilience")
  class ResilienceTests {

    @Test
    @DisplayName("delegates to triggerService on happy path")
    void delegatesToTriggerService() {
      WorkOrderApprovedEvent event = buildEvent();

      assertThatCode(() -> listener.handleWorkOrderApprovedEvent(event)).doesNotThrowAnyException();

      verify(plannedCostTriggerService).triggerPlannedCost(WORK_ORDER_ID);
    }

    @Test
    @DisplayName("sets TenantContext from event before delegation on async thread")
    void setsTenantContextFromEventBeforeDelegation() {
      TenantContext.clear();
      doAnswer(
              invocation -> {
                assertThat(TenantContext.requireTenantId()).isEqualTo(TENANT_ID);
                return null;
              })
          .when(plannedCostTriggerService)
          .triggerPlannedCost(WORK_ORDER_ID);

      WorkOrderApprovedEvent event = buildEvent();

      assertThatCode(() -> listener.handleWorkOrderApprovedEvent(event)).doesNotThrowAnyException();

      verify(plannedCostTriggerService).triggerPlannedCost(WORK_ORDER_ID);
      assertThat(TenantContext.getCurrentTenantIdOrNull()).isNull();
    }

    @Test
    @DisplayName("swallows RuntimeException so approval flow is not failed by costing")
    void swallowsRuntimeException() {
      doThrow(new RuntimeException("Price list not found"))
          .when(plannedCostTriggerService)
          .triggerPlannedCost(any());

      WorkOrderApprovedEvent event = buildEvent();

      assertThatCode(() -> listener.handleWorkOrderApprovedEvent(event)).doesNotThrowAnyException();

      verify(plannedCostTriggerService).triggerPlannedCost(WORK_ORDER_ID);
    }

    @Test
    @DisplayName("swallows WorkOrderDomainException so approval flow is not failed by costing")
    void swallowsDomainException() {
      doThrow(
              new com.fabricmanagement.production.execution.workorder.domain.exception
                  .WorkOrderDomainException("missing outputProductId"))
          .when(plannedCostTriggerService)
          .triggerPlannedCost(any());

      WorkOrderApprovedEvent event = buildEvent();

      assertThatCode(() -> listener.handleWorkOrderApprovedEvent(event)).doesNotThrowAnyException();

      verify(plannedCostTriggerService).triggerPlannedCost(WORK_ORDER_ID);
    }

    @Test
    @DisplayName("swallows unexpected exception so approval flow is not failed by costing")
    void swallowsUnexpectedNPE() {
      doThrow(new NullPointerException("unexpected"))
          .when(plannedCostTriggerService)
          .triggerPlannedCost(any());

      WorkOrderApprovedEvent event = buildEvent();

      assertThatCode(() -> listener.handleWorkOrderApprovedEvent(event)).doesNotThrowAnyException();

      verify(plannedCostTriggerService).triggerPlannedCost(WORK_ORDER_ID);
    }
  }
}
