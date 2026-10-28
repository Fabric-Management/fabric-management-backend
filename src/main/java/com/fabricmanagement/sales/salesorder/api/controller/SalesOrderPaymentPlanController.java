package com.fabricmanagement.sales.salesorder.api.controller;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.salesorder.app.CustomerApprovalService;
import com.fabricmanagement.sales.salesorder.app.PaymentPlanPreviewService;
import com.fabricmanagement.sales.salesorder.app.SalesOrderPaymentPlanService;
import com.fabricmanagement.sales.salesorder.dto.CustomerApprovalDtos;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.Preview;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.PreviewResult;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.Revision;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.Save;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.Submit;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.View;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.responses.ApiResponse(
    responseCode = "409",
    description = "Payment plan state, readiness or version conflict",
    content =
        @io.swagger.v3.oas.annotations.media.Content(
            mediaType = "application/problem+json",
            schema =
                @io.swagger.v3.oas.annotations.media.Schema(
                    implementation =
                        com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.Conflict
                            .class)))
@Tag(
    name = "Sales Order Payment Plans",
    description = "Versioned commercial payment plans and customer agreement")
public class SalesOrderPaymentPlanController {
  private final SalesOrderPaymentPlanService plans;
  private final PaymentPlanPreviewService previews;
  private final CustomerApprovalService approvals;

  @PostMapping("/api/v1/sales/payment-plans/preview")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "previewSalesPaymentPlan",
      summary = "Calculate a draft payment plan without saving an order")
  public ResponseEntity<ApiResponse<PreviewResult>> preview(@Valid @RequestBody Preview request) {
    return ResponseEntity.ok(ApiResponse.success(previews.preview(request)));
  }

  @GetMapping("/api/v1/sales/orders/{orderId}/payment-plan")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "getSalesOrderPaymentPlan",
      summary = "Read draft and agreed revisions with readiness and capabilities")
  public ResponseEntity<ApiResponse<View>> read(
      @PathVariable UUID orderId, Authentication authentication) {
    return ResponseEntity.ok(ApiResponse.success(plans.read(orderId, actor(authentication))));
  }

  @PostMapping("/api/v1/sales/orders/{orderId}/payment-plan/revisions")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "saveSalesOrderPaymentPlanRevision",
      summary =
          "Append an immutable payment plan draft with optimistic versions and replay protection")
  public ResponseEntity<ApiResponse<Revision>> save(
      @PathVariable UUID orderId, @Valid @RequestBody Save request, Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(plans.save(orderId, request, actor(authentication))));
  }

  @GetMapping("/api/v1/sales/orders/{orderId}/payment-plan/revisions")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "listSalesOrderPaymentPlanRevisions",
      summary = "Read the paged revision and agreement history")
  public ResponseEntity<ApiResponse<Page<Revision>>> history(
      @PathVariable UUID orderId, Pageable pageable, Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(plans.history(orderId, actor(authentication), pageable)));
  }

  @PostMapping("/api/v1/sales/orders/{orderId}/payment-plan/revisions/{revisionId}/submit")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "submitSalesOrderPaymentPlan",
      summary = "Submit this exact revision through the customer approval policy")
  public ResponseEntity<ApiResponse<CustomerApprovalDtos.State>> submit(
      @PathVariable UUID orderId,
      @PathVariable UUID revisionId,
      @Valid @RequestBody Submit request,
      Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(
            approvals.submitPaymentPlan(orderId, revisionId, request, actor(authentication))));
  }

  private static UUID actor(Authentication authentication) {
    if (authentication != null
        && authentication.getPrincipal() instanceof AuthenticatedUserContext context
        && context.userId() != null) return context.userId();
    throw new AccessDeniedException("A signed-in user is required");
  }
}
