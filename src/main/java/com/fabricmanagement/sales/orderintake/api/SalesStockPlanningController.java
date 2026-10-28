package com.fabricmanagement.sales.orderintake.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.orderintake.app.SalesStockPlanningService;
import com.fabricmanagement.sales.orderintake.dto.SalesStockPlanningDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sales/order-intake")
@RequiredArgsConstructor
@Tag(
    name = "Sales stock planning",
    description = "Physical assignment and outstanding planning work")
@io.swagger.v3.oas.annotations.responses.ApiResponses({
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "404",
      description = "Document or product line is missing or outside the current actor scope",
      content =
          @io.swagger.v3.oas.annotations.media.Content(
              mediaType = "application/problem+json",
              schema =
                  @io.swagger.v3.oas.annotations.media.Schema(
                      implementation =
                          com.fabricmanagement.common.infrastructure.web.exception.ApiProblemDetail
                              .class))),
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "409",
      description =
          "STALE_VERSION, IDEMPOTENCY_KEY_REUSED, STOCK_PROPOSAL_STALE, STOCK_TARGET_CHANGED or"
              + " QUOTE_NOT_EDITABLE; refresh the affected document",
      content =
          @io.swagger.v3.oas.annotations.media.Content(
              mediaType = "application/problem+json",
              schema =
                  @io.swagger.v3.oas.annotations.media.Schema(
                      implementation =
                          com.fabricmanagement.common.infrastructure.web.exception.ApiProblemDetail
                              .class))),
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "422",
      description =
          "EXACT_QUANTITY_REQUIRED, QUANTITY_DECISION_REQUIRED or CONFIRMATION_REQUIRED; record the"
              + " quantity decision before applying",
      content =
          @io.swagger.v3.oas.annotations.media.Content(
              mediaType = "application/problem+json",
              schema =
                  @io.swagger.v3.oas.annotations.media.Schema(
                      implementation =
                          com.fabricmanagement.common.infrastructure.web.exception.ApiProblemDetail
                              .class)))
})
public class SalesStockPlanningController {
  private final SalesStockPlanningService service;
  private final com.fabricmanagement.sales.orderintake.app.SalesQuantityPolicyService policies;

  @PostMapping("/orders/{orderId}/lines/{lineId}/quantity-policy")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "recordOrderQuantityPolicy",
      summary = "Record the exact quantity decision with version and edit-lease checks")
  public ApiResponse<com.fabricmanagement.sales.orderintake.dto.SalesQuantityPolicyResultDto>
      policy(
          @PathVariable UUID orderId,
          @PathVariable UUID lineId,
          @jakarta.validation.Valid @RequestBody
              com.fabricmanagement.sales.quote.dto.QuoteRollSelectionRequests.Policy request) {
    return ApiResponse.success(
        policies.record(orderId, lineId, request, OrderIntakeActor.current()));
  }

  @GetMapping("/orders/{orderId}/lines/{lineId}/stock-planning")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "getOrderLineStockPlanning",
      summary = "Read quantity policy, physical assignment and planning capabilities")
  public ApiResponse<SalesStockPlanningDto> order(
      @PathVariable UUID orderId, @PathVariable UUID lineId) {
    return ApiResponse.success(service.order(orderId, lineId, OrderIntakeActor.current()));
  }

  @GetMapping("/quotes/{quoteId}/lines/{lineId}/stock-planning")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "getQuoteLineStockPlanning",
      summary = "Distinguish quote metre intents from actual pre-reserved rolls")
  public ApiResponse<SalesStockPlanningDto> quote(
      @PathVariable UUID quoteId, @PathVariable UUID lineId) {
    return ApiResponse.success(service.quote(quoteId, lineId, OrderIntakeActor.current()));
  }
}
