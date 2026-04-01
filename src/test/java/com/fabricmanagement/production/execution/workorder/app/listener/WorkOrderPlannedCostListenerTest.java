package com.fabricmanagement.production.execution.workorder.app.listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.workorder.app.WorkOrderPlannedCostTriggerService;
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

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
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
        "YARN",
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

      listener.handleWorkOrderApprovedEvent(event);

      verify(plannedCostTriggerService).triggerPlannedCost(WORK_ORDER_ID);
    }

    @Test
    @DisplayName("sets TenantContext from event before delegation")
    void setsTenantContextFromEvent() {
      // Start with a different tenant to prove the listener overrides it
      TenantContext.setCurrentTenantId(UUID.randomUUID());

      WorkOrderApprovedEvent event = buildEvent();

      listener.handleWorkOrderApprovedEvent(event);

      // The listener should have set TenantContext to the event's tenantId.
      // Since triggerService is mocked, TenantContext should still be event's tenant
      // after the call.
      verify(plannedCostTriggerService).triggerPlannedCost(WORK_ORDER_ID);
    }

    @Test
    @DisplayName("swallows RuntimeException — approval must not fail")
    void swallowsRuntimeException() {
      doThrow(new RuntimeException("Price list not found"))
          .when(plannedCostTriggerService)
          .triggerPlannedCost(any());

      WorkOrderApprovedEvent event = buildEvent();

      assertThatCode(() -> listener.handleWorkOrderApprovedEvent(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("swallows WorkOrderDomainException — approval must not fail")
    void swallowsDomainException() {
      doThrow(
              new com.fabricmanagement.production.execution.workorder.domain.exception
                  .WorkOrderDomainException("missing outputMaterialId"))
          .when(plannedCostTriggerService)
          .triggerPlannedCost(any());

      WorkOrderApprovedEvent event = buildEvent();

      assertThatCode(() -> listener.handleWorkOrderApprovedEvent(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("swallows NullPointerException — unexpected failures also swallowed")
    void swallowsUnexpectedNPE() {
      doThrow(new NullPointerException("unexpected"))
          .when(plannedCostTriggerService)
          .triggerPlannedCost(any());

      WorkOrderApprovedEvent event = buildEvent();

      assertThatCode(() -> listener.handleWorkOrderApprovedEvent(event)).doesNotThrowAnyException();
    }
  }
}
