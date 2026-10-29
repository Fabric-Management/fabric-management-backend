package com.fabricmanagement.production.core.stockunit.api;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.core.stockunit.app.StockUnitCutPlanService;
import com.fabricmanagement.production.core.stockunit.dto.StockUnitCutPlanDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.responses.ApiResponses({
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "400",
      description =
          "Invalid cut measurements or command; CUT_PLAN_* code identifies the rejected input",
      content =
          @io.swagger.v3.oas.annotations.media.Content(
              mediaType = "application/problem+json",
              schema =
                  @io.swagger.v3.oas.annotations.media.Schema(
                      implementation =
                          com.fabricmanagement.common.infrastructure.web.exception.ApiProblemDetail
                              .class))),
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "403",
      description = "Current actor permission or target scope does not allow this action"),
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "404",
      description = "The target or cut plan is not visible in this tenant"),
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "409",
      description =
          "CUT_PLAN_SELECTION_CHANGED, CUT_PLAN_SOURCE_CHANGED or CUT_PLAN_* conflict: retain local input and reload current evidence; reuse the same operation ID after an uncertain response",
      content =
          @io.swagger.v3.oas.annotations.media.Content(
              mediaType = "application/problem+json",
              schema =
                  @io.swagger.v3.oas.annotations.media.Schema(
                      implementation =
                          com.fabricmanagement.common.infrastructure.web.exception.ApiProblemDetail
                              .class)))
})
@RestController
@RequestMapping("/api/v1/production/stock-cut-plans")
@RequiredArgsConstructor
@Tag(name = "Stock cut plans", description = "Source planning, cutting and verified child stock")
public class StockUnitCutPlanController {
  private final StockUnitCutPlanService plans;

  @GetMapping("/targets/{type}/{documentId}/lines/{lineId}/view")
  @PreAuthorize(
      "@auth.can(authentication, 'production', 'read') or @auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "getStockUnitCutTarget",
      summary = "Read exact demand and allowed source-planning actions without acquiring stock")
  public ApiResponse<TargetView> target(
      @PathVariable StockUnitCutTargetPort.TargetType type,
      @PathVariable UUID documentId,
      @PathVariable UUID lineId) {
    return ApiResponse.success(
        plans.targetView(new StockUnitCutTargetPort.Target(type, documentId, lineId), actor()));
  }

  @GetMapping("/targets/{type}/{documentId}/lines/{lineId}")
  @PreAuthorize(
      "@auth.can(authentication, 'production', 'read') or @auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "listStockUnitCutPlans",
      summary = "Read cut plans for a verified commercial target")
  public ApiResponse<List<View>> list(
      @PathVariable StockUnitCutTargetPort.TargetType type,
      @PathVariable UUID documentId,
      @PathVariable UUID lineId) {
    return ApiResponse.success(
        plans.list(new StockUnitCutTargetPort.Target(type, documentId, lineId), actor()));
  }

  @GetMapping("/{id}")
  @PreAuthorize(
      "@auth.can(authentication, 'production', 'read') or @auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "getStockUnitCutPlan",
      summary = "Read source, actual, lineage and permitted cut actions")
  public ApiResponse<View> get(@PathVariable UUID id) {
    return ApiResponse.success(plans.get(id, actor()));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(
      operationId = "approveStockUnitCutPlan",
      summary = "Review this exact version of the source plan")
  public ApiResponse<View> approve(@PathVariable UUID id, @Valid @RequestBody Command request) {
    return command(id, Action.APPROVE, request);
  }

  @PostMapping("/{id}/record")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(
      operationId = "recordStockUnitCutPlanActual",
      summary = "Record actual measurements without releasing future stock")
  public ApiResponse<View> record(@PathVariable UUID id, @Valid @RequestBody Command request) {
    return command(id, Action.RECORD, request);
  }

  @PostMapping("/{id}/correct")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(
      operationId = "correctStockUnitCutPlanActual",
      summary = "Resolve disputed measurements and retain earlier operation receipts")
  public ApiResponse<View> correct(@PathVariable UUID id, @Valid @RequestBody Command request) {
    return command(id, Action.CORRECT, request);
  }

  @PostMapping("/{id}/verify")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(
      operationId = "verifyStockUnitCutPlanActual",
      summary = "Verify conservation and replace the source with traceable child stock atomically")
  public ApiResponse<View> verify(@PathVariable UUID id, @Valid @RequestBody Command request) {
    return command(id, Action.VERIFY, request);
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize(
      "@auth.can(authentication, 'production', 'write') or @auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "cancelStockUnitCutPlan",
      summary = "Cancel an uncut plan and release its sources")
  public ApiResponse<View> cancel(@PathVariable UUID id, @Valid @RequestBody Command request) {
    return command(id, Action.CANCEL, request);
  }

  private ApiResponse<View> command(UUID id, Action action, Command request) {
    return ApiResponse.success(plans.command(id, action, request, actor()));
  }

  private UUID actor() {
    return java.util.Objects.requireNonNull(TenantContext.getCurrentUserId());
  }
}
