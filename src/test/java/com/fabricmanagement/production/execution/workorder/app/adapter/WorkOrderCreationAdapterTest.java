package com.fabricmanagement.production.execution.workorder.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.production.execution.workorder.app.WorkOrderService;
import com.fabricmanagement.production.execution.workorder.dto.CreateWorkOrderRequest;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderResponse;
import com.fabricmanagement.sales.salesorder.domain.port.DraftProductionOrderCommand;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkOrderCreationAdapter — DraftProductionOrderCommand → CreateWorkOrderRequest")
class WorkOrderCreationAdapterTest {

  @Mock private WorkOrderService workOrderService;

  @InjectMocks private WorkOrderCreationAdapter adapter;

  @Captor private ArgumentCaptor<CreateWorkOrderRequest> requestCaptor;

  @Test
  @DisplayName("maps all command fields including certificationReq and originReq")
  void mapsAllFieldsIncludingCertOrigin() {
    UUID recipeId = UUID.randomUUID();
    UUID tradingPartnerId = UUID.randomUUID();
    UUID salesOrderLineId = UUID.randomUUID();
    LocalDate deadline = LocalDate.of(2026, 7, 1);

    DraftProductionOrderCommand cmd =
        new DraftProductionOrderCommand(
            recipeId,
            tradingPartnerId,
            salesOrderLineId,
            new BigDecimal("500.000"),
            "KG",
            "TRY",
            deadline,
            "GOTS",
            "TR");

    when(workOrderService.createWorkOrder(any(CreateWorkOrderRequest.class)))
        .thenReturn(WorkOrderResponse.builder().build());

    adapter.requestDraftProductionOrder(cmd);

    verify(workOrderService).createWorkOrder(requestCaptor.capture());
    CreateWorkOrderRequest captured = requestCaptor.getValue();

    assertThat(captured.recipeId()).isEqualTo(recipeId);
    assertThat(captured.tradingPartnerId()).isEqualTo(tradingPartnerId);
    assertThat(captured.salesOrderLineId()).isEqualTo(salesOrderLineId);
    assertThat(captured.plannedQty()).isEqualByComparingTo(new BigDecimal("500.000"));
    assertThat(captured.unit()).isEqualTo("KG");
    assertThat(captured.currency()).isEqualTo("TRY");
    assertThat(captured.deadline()).isEqualTo(deadline);
    assertThat(captured.certificationReq()).isEqualTo("GOTS");
    assertThat(captured.originReq()).isEqualTo("TR");
  }

  @Test
  @DisplayName("passes null cert/origin through without modification")
  void passesNullCertOriginThrough() {
    DraftProductionOrderCommand cmd =
        new DraftProductionOrderCommand(
            null, null, UUID.randomUUID(), BigDecimal.TEN, "M", "USD", null, null, null);

    when(workOrderService.createWorkOrder(any(CreateWorkOrderRequest.class)))
        .thenReturn(WorkOrderResponse.builder().build());

    adapter.requestDraftProductionOrder(cmd);

    verify(workOrderService).createWorkOrder(requestCaptor.capture());
    CreateWorkOrderRequest captured = requestCaptor.getValue();

    assertThat(captured.certificationReq()).isNull();
    assertThat(captured.originReq()).isNull();
  }
}
