package com.fabricmanagement.production.execution.workorder.app.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.approval.domain.event.ApprovalApprovedEvent;
import com.fabricmanagement.platform.approval.domain.event.ApprovalRejectedEvent;
import com.fabricmanagement.production.execution.workorder.app.WorkOrderService;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
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
@DisplayName("WorkOrderApprovalEventListener")
class WorkOrderApprovalEventListenerTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID WORK_ORDER_ID = UUID.randomUUID();

  @Mock private WorkOrderService workOrderService;
  @Mock private IdempotentEventHandler idempotentHandler;

  @InjectMocks private WorkOrderApprovalEventListener listener;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.lenient()
        .doAnswer(
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
  @DisplayName("sets TenantContext before applying approval")
  void setsTenantContextBeforeApplyingApproval() {
    doAnswer(
            invocation -> {
              assertThat(TenantContext.requireTenantId()).isEqualTo(TENANT_ID);
              return null;
            })
        .when(workOrderService)
        .changeStatus(WORK_ORDER_ID, WorkOrderStatus.APPROVED);

    listener.onApprovalApproved(buildApprovedEvent("WORK_ORDER"));

    verify(workOrderService).changeStatus(WORK_ORDER_ID, WorkOrderStatus.APPROVED);
    assertThat(TenantContext.getCurrentTenantIdOrNull()).isNull();
  }

  @Test
  @DisplayName("sets TenantContext before applying rejection")
  void setsTenantContextBeforeApplyingRejection() {
    doAnswer(
            invocation -> {
              assertThat(TenantContext.requireTenantId()).isEqualTo(TENANT_ID);
              return null;
            })
        .when(workOrderService)
        .changeStatus(WORK_ORDER_ID, WorkOrderStatus.CANCELLED);

    listener.onApprovalRejected(buildRejectedEvent("WORK_ORDER"));

    verify(workOrderService).changeStatus(WORK_ORDER_ID, WorkOrderStatus.CANCELLED);
    assertThat(TenantContext.getCurrentTenantIdOrNull()).isNull();
  }

  @Test
  @DisplayName("ignores approvals for other entity types")
  void ignoresOtherEntityTypes() {
    listener.onApprovalApproved(buildApprovedEvent("SALES_ORDER"));

    verify(workOrderService, never()).changeStatus(any(), any());
  }

  private ApprovalApprovedEvent buildApprovedEvent(String entityType) {
    return new ApprovalApprovedEvent(
        TENANT_ID, UUID.randomUUID(), entityType, WORK_ORDER_ID, "WO-TEST-003", UUID.randomUUID());
  }

  private ApprovalRejectedEvent buildRejectedEvent(String entityType) {
    return new ApprovalRejectedEvent(
        TENANT_ID,
        UUID.randomUUID(),
        entityType,
        WORK_ORDER_ID,
        "WO-TEST-003",
        UUID.randomUUID(),
        "Not approved");
  }
}
