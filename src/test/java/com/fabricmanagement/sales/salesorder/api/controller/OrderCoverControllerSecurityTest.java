package com.fabricmanagement.sales.salesorder.api.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.flowboard.task.api.DecisionTaskTransitionController;
import com.fabricmanagement.flowboard.task.app.OrderCoverTaskResolver;
import com.fabricmanagement.flowboard.task.app.TaskTransitionExecutor;
import com.fabricmanagement.flowboard.task.dto.DecisionTransitionRequest;
import com.fabricmanagement.sales.salesorder.app.OrderCoverEvidenceService;
import com.fabricmanagement.sales.salesorder.app.OrderCoverPreviewService;
import com.fabricmanagement.sales.salesorder.app.OrderCoverQueryService;
import com.fabricmanagement.sales.salesorder.dto.ConfirmProductionCoverPayload;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverSelectionPreviewRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

class OrderCoverControllerSecurityTest {
  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void missingReadGrantReturnsNotFoundBeforeLoadingTheCoverCase() {
    OrderCoverQueryService query = mock(OrderCoverQueryService.class);
    OrderCoverEvidenceService evidence = mock(OrderCoverEvidenceService.class);
    OrderCoverPreviewService preview = mock(OrderCoverPreviewService.class);
    var controller =
        new OrderCoverController(query, evidence, preview, mock(SpELPermissionEvaluator.class));

    assertThatThrownBy(() -> controller.getOrderCoverCase(UUID.randomUUID()))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Sales order not found");
    verifyNoInteractions(query, evidence, preview);
  }

  @Test
  void previewUsesTheSameReadGrantBoundaryAsTheDetail() {
    OrderCoverQueryService query = mock(OrderCoverQueryService.class);
    OrderCoverEvidenceService evidence = mock(OrderCoverEvidenceService.class);
    OrderCoverPreviewService preview = mock(OrderCoverPreviewService.class);
    var controller =
        new OrderCoverController(query, evidence, preview, mock(SpELPermissionEvaluator.class));

    assertThatThrownBy(
            () ->
                controller.previewOrderCoverSelection(
                    UUID.randomUUID(),
                    new OrderCoverSelectionPreviewRequest(
                        UUID.randomUUID(), 1, List.of(UUID.randomUUID()))))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Sales order not found");
    verifyNoInteractions(query, evidence, preview);
  }

  @Test
  void missingReadGrantReturnsNotFoundBeforeLoadingTheDecisionTask() {
    OrderCoverTaskResolver orderCoverTasks = mock(OrderCoverTaskResolver.class);
    OrderCoverQueryService query = mock(OrderCoverQueryService.class);
    TaskTransitionExecutor transitions = mock(TaskTransitionExecutor.class);
    var controller =
        new DecisionTaskTransitionController(
            orderCoverTasks, query, transitions, mock(SpELPermissionEvaluator.class));
    var request =
        new DecisionTransitionRequest(
            DecisionTransitionRequest.Action.CONFIRM_PRODUCTION_COVER,
            0L,
            UUID.randomUUID(),
            new ConfirmProductionCoverPayload(
                UUID.randomUUID(), UUID.randomUUID(), 1, List.of(UUID.randomUUID()), null, null));

    assertThatThrownBy(() -> controller.transitionDecisionTask(UUID.randomUUID(), request))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Decision task not found");
    verifyNoInteractions(orderCoverTasks, query, transitions);
  }
}
