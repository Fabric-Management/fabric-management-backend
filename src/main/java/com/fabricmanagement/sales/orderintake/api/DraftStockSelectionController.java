package com.fabricmanagement.sales.orderintake.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.orderintake.app.DraftStockSelectionService;
import com.fabricmanagement.sales.orderintake.dto.DraftStockSelectionDto;
import com.fabricmanagement.sales.orderintake.dto.DraftStockSelectionRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sales/order-intake/stock-selections")
@RequiredArgsConstructor
@Tag(
    name = "Draft stock selections",
    description = "Short-lived roll ownership before a commercial stock choice")
@io.swagger.v3.oas.annotations.responses.ApiResponses({
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "403",
      description = "Current sales write permission or object scope refused",
      content =
          @io.swagger.v3.oas.annotations.media.Content(
              mediaType = "application/problem+json",
              schema =
                  @io.swagger.v3.oas.annotations.media.Schema(
                      implementation =
                          com.fabricmanagement.sales.orderintake.dto.DraftStockSelectionProblemDto
                              .class))),
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "409",
      description =
          "Stock, version, session, ownership period or operation conflict; refresh the named line",
      content =
          @io.swagger.v3.oas.annotations.media.Content(
              mediaType = "application/problem+json",
              schema =
                  @io.swagger.v3.oas.annotations.media.Schema(
                      implementation =
                          com.fabricmanagement.sales.orderintake.dto.DraftStockSelectionProblemDto
                              .class))),
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "422",
      description = "Incomplete terms, invalid source or confirmation required",
      content =
          @io.swagger.v3.oas.annotations.media.Content(
              mediaType = "application/problem+json",
              schema =
                  @io.swagger.v3.oas.annotations.media.Schema(
                      implementation =
                          com.fabricmanagement.sales.orderintake.dto.DraftStockSelectionProblemDto
                              .class)))
})
public class DraftStockSelectionController {
  private final DraftStockSelectionService service;

  @PostMapping("/{id}/cut-evaluation")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "evaluateDraftStockCut",
      summary = "Evaluate an explicit exact cut using this draft's verified whole-source proof")
  public ResponseEntity<
          ApiResponse<com.fabricmanagement.production.core.stockunit.dto.StockUnitCutPlanDtos.View>>
      previewCut(
          @PathVariable UUID id, @Valid @RequestBody DraftStockSelectionRequests.Proof request) {
    return ResponseEntity.ok(
        ApiResponse.success(service.previewCut(id, request, OrderIntakeActor.current())));
  }

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "openDraftStockSelection",
      summary = "Open the stock session of one unsaved form line")
  public ResponseEntity<ApiResponse<DraftStockSelectionDto>> open(
      @Valid @RequestBody DraftStockSelectionRequests.Open request) {
    return ResponseEntity.ok(
        ApiResponse.success(service.open(request, OrderIntakeActor.current())));
  }

  @PostMapping("/{id}/preview")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "previewDraftStockTarget",
      summary = "Read stock for the verified saved target before acquiring any rolls",
      description =
          "Excludes only the authenticated target line's existing commitment. Does not acquire,"
              + " renew or reserve stock.")
  public ResponseEntity<
          ApiResponse<com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewDto>>
      preview(
          @PathVariable UUID id,
          @Valid @RequestBody
              com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(service.preview(id, request, OrderIntakeActor.current())));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "getDraftStockSelection",
      summary = "Read this session's ownership and backend policy")
  public ResponseEntity<ApiResponse<DraftStockSelectionDto>> status(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(service.status(id, OrderIntakeActor.current())));
  }

  @PostMapping("/{id}/acquire")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "acquireDraftStockSelection",
      summary = "Acquire or replace the active displayed roll proposal atomically",
      description =
          "Called when the proposal is presented, before Apply. All requested rolls or none."
              + " Requires sales write; read-only preview never acquires stock. A retry carries the"
              + " same operationId and payload.")
  public ResponseEntity<ApiResponse<DraftStockSelectionDto>> acquire(
      @PathVariable UUID id, @Valid @RequestBody DraftStockSelectionRequests.Acquire request) {
    return ResponseEntity.ok(
        ApiResponse.success(service.acquire(id, request, OrderIntakeActor.current())));
  }

  @PostMapping("/{id}/renew")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "renewDraftStockSelection",
      summary = "Renew only this session's current ownership periods")
  public ResponseEntity<ApiResponse<DraftStockSelectionDto>> renew(
      @PathVariable UUID id, @Valid @RequestBody DraftStockSelectionRequests.Tokens request) {
    return ResponseEntity.ok(
        ApiResponse.success(service.renew(id, request, OrderIntakeActor.current())));
  }

  @PostMapping("/{id}/release")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "releaseDraftStockSelection",
      summary = "Release this preview; repeated or obsolete tokens change nothing")
  public ResponseEntity<Void> release(
      @PathVariable UUID id, @Valid @RequestBody DraftStockSelectionRequests.Tokens request) {
    service.release(id, request, OrderIntakeActor.current());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/close")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "closeDraftStockSelection",
      summary = "Close this form session and end its current ownership periods")
  public ResponseEntity<Void> close(
      @PathVariable UUID id, @Valid @RequestBody DraftStockSelectionRequests.Close request) {
    service.close(id, request, OrderIntakeActor.current());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/orders/{orderId}/lines/{lineId}/apply")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "applyDraftStockSelection",
      summary = "Apply and transfer a displayed selection to a saved draft order line",
      description =
          "Checks the order version and existing edit-field leases. Salesperson confirmation is not"
              + " customer consent. New-order creation carries the same proof within its atomic"
              + " create request.")
  public ResponseEntity<ApiResponse<DraftStockSelectionDto>> apply(
      @PathVariable UUID orderId,
      @PathVariable UUID lineId,
      @Valid @RequestBody DraftStockSelectionRequests.Apply request) {
    return ResponseEntity.ok(
        ApiResponse.success(service.apply(orderId, lineId, request, OrderIntakeActor.current())));
  }

  @DeleteMapping("/orders/{orderId}/lines/{lineId}/selection")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "removeAppliedStockSelection",
      summary = "Remove an applied draft stock choice without changing its commercial quantity",
      description =
          "Versioned and idempotent. Uses the existing quantity/whole-line edit lease guard;"
              + " releases only this order line's permanent holds.")
  public ResponseEntity<ApiResponse<DraftStockSelectionDto>> remove(
      @PathVariable UUID orderId,
      @PathVariable UUID lineId,
      @Valid @RequestBody DraftStockSelectionRequests.Remove request) {
    return ResponseEntity.ok(
        ApiResponse.success(service.remove(orderId, lineId, request, OrderIntakeActor.current())));
  }
}
