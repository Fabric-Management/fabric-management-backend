package com.fabricmanagement.production.core.stockunit.api;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.core.stockunit.app.StockUnitCutSessionService;
import com.fabricmanagement.production.core.stockunit.dto.StockUnitCutPlanDtos;
import com.fabricmanagement.production.core.stockunit.dto.StockUnitCutSessionDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/production/stock-cut-sessions")
@RequiredArgsConstructor
@PreAuthorize(
    "@auth.can(authentication, 'production', 'write') or @auth.can(authentication, 'sales', 'write')")
@Tag(
    name = "Stock cut plans",
    description = "Explicit source selection on the common stock lease runtime")
public class StockUnitCutSessionController {
  private final StockUnitCutSessionService sessions;

  @PostMapping
  @Operation(
      operationId = "openStockUnitCutSession",
      summary = "Open a target-bound cut source selection")
  public ApiResponse<View> open(@Valid @RequestBody Open request) {
    return ApiResponse.success(sessions.open(request, actor()));
  }

  @GetMapping("/{id}")
  @Operation(
      operationId = "getStockUnitCutSession",
      summary = "Read this actor's current source selection and private proof")
  public ApiResponse<View> get(@PathVariable UUID id) {
    return ApiResponse.success(sessions.status(id, actor()));
  }

  @PutMapping("/{id}/sources")
  @Operation(
      operationId = "replaceStockUnitCutSources",
      summary = "Hold complete sources atomically and evaluate the explicit exact cut")
  public ApiResponse<View> pick(@PathVariable UUID id, @Valid @RequestBody Pick request) {
    return ApiResponse.success(sessions.pick(id, request, actor()));
  }

  @PostMapping("/{id}/renew")
  @Operation(
      operationId = "renewStockUnitCutSession",
      summary = "Renew current source periods while the person is active")
  public ApiResponse<View> renew(@PathVariable UUID id, @Valid @RequestBody Tokens request) {
    return ApiResponse.success(sessions.renew(id, request, actor()));
  }

  @PostMapping("/{id}/close")
  @Operation(
      operationId = "closeStockUnitCutSession",
      summary = "Close this source selection without releasing a transferred plan")
  public ApiResponse<View> close(@PathVariable UUID id, @Valid @RequestBody Tokens request) {
    return ApiResponse.success(sessions.close(id, request, actor()));
  }

  @PostMapping("/{id}/release")
  @Operation(
      operationId = "releaseStockUnitCutSession",
      summary = "Release only this source period and retain the open selector")
  public ApiResponse<View> release(@PathVariable UUID id, @Valid @RequestBody Tokens request) {
    return ApiResponse.success(sessions.release(id, request, actor()));
  }

  @PostMapping("/{id}/apply")
  @Operation(
      operationId = "applyStockUnitCutSession",
      summary = "Attach an exact cut plan without changing the commercial quantity")
  public ApiResponse<StockUnitCutPlanDtos.View> apply(
      @PathVariable UUID id, @Valid @RequestBody Apply request) {
    return ApiResponse.success(sessions.apply(id, request, actor()));
  }

  private UUID actor() {
    return java.util.Objects.requireNonNull(TenantContext.getCurrentUserId());
  }
}
