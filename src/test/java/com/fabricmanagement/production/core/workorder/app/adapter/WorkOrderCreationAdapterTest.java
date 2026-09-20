package com.fabricmanagement.production.core.workorder.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.production.core.workorder.app.WorkOrderService;
import com.fabricmanagement.production.core.workorder.dto.CreateWorkOrderRequest;
import com.fabricmanagement.production.core.workorder.dto.WorkOrderResponse;
import com.fabricmanagement.sales.salesorder.domain.port.DraftProductionOrderCommand;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileBasis;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
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
            "GBP",
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
    assertThat(captured.currency()).isEqualTo("GBP");
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

  @Test
  void carriesImmutableRequirementProfileBindingIntoTheDraftRequest() {
    UUID orderId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID profileId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    RequirementProfileSnapshot profile =
        new RequirementProfileSnapshot(
            profileId,
            3,
            new RequirementProfileBasis(
                RequirementProfileBasis.Kind.LINE_EXPLICIT,
                productId,
                null,
                null,
                null,
                actorId,
                Instant.parse("2026-09-19T12:00:00Z"),
                "customer instruction"),
            "v1",
            "v1",
            Set.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            true,
            List.of(),
            "fingerprint");
    DraftProductionOrderCommand command =
        new DraftProductionOrderCommand(
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            BigDecimal.TEN,
            "M",
            "GBP",
            null,
            null,
            null,
            orderId,
            productId,
            profileId,
            3,
            profile,
            "FABRIC-001");
    WorkOrderCreationAdapter profileAdapter =
        new WorkOrderCreationAdapter(
            workOrderService,
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
    when(workOrderService.createWorkOrder(any(CreateWorkOrderRequest.class)))
        .thenReturn(WorkOrderResponse.builder().id(UUID.randomUUID()).build());

    profileAdapter.requestDraftProductionOrder(command);

    verify(workOrderService).createWorkOrder(requestCaptor.capture());
    assertThat(requestCaptor.getValue().salesOrderId()).isEqualTo(orderId);
    assertThat(requestCaptor.getValue().outputProductId()).isEqualTo(productId);
    assertThat(requestCaptor.getValue().requirementProfileId()).isEqualTo(profileId);
    assertThat(requestCaptor.getValue().requirementProfileVersion()).isEqualTo(3);
    assertThat(requestCaptor.getValue().requirementProfileSnapshot()).isNotNull();
    assertThat(requestCaptor.getValue().productCode()).isEqualTo("FABRIC-001");
  }
}
