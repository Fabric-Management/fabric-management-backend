package com.fabricmanagement.sales.quote.api;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.quote.app.QuoteRollSelectionService;
import com.fabricmanagement.sales.quote.dto.QuoteRollSelectionDto;
import com.fabricmanagement.sales.quote.dto.QuoteRollSelectionRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sales/quotes/{quoteId}")
@RequiredArgsConstructor
@Tag(
    name = "Quote roll selections",
    description =
        "Optional physical roll pre-reservation; metre-only quote entry remains available")
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
public class QuoteRollSelectionController {
  private final QuoteRollSelectionService service;

  @PostMapping("/lines/{lineId}/stock-selection")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "applyQuoteRollSelection",
      summary =
          "Atomically apply the displayed rolls and commercial quantity to a draft quote line")
  public ResponseEntity<ApiResponse<QuoteRollSelectionDto>> apply(
      @PathVariable UUID quoteId,
      @PathVariable UUID lineId,
      @Valid @RequestBody QuoteRollSelectionRequests.Apply request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            service.apply(quoteId, lineId, request, TenantContext.getCurrentUserId())));
  }

  @PostMapping("/stock-lines")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "createQuoteStockLine",
      summary = "Save a new quote line and transfer its provisional rolls in one transaction")
  public ResponseEntity<ApiResponse<QuoteRollSelectionDto>> create(
      @PathVariable UUID quoteId, @Valid @RequestBody QuoteRollSelectionRequests.Create request) {
    return ResponseEntity.ok(
        ApiResponse.success(service.create(quoteId, request, TenantContext.getCurrentUserId())));
  }

  @DeleteMapping("/lines/{lineId}/stock-selection")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "removeQuoteRollSelection",
      summary = "Release the quote line's stock without changing its commercial quantity")
  public ResponseEntity<ApiResponse<QuoteRollSelectionDto>> remove(
      @PathVariable UUID quoteId,
      @PathVariable UUID lineId,
      @Valid @RequestBody QuoteRollSelectionRequests.Remove request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            service.remove(quoteId, lineId, request, TenantContext.getCurrentUserId())));
  }

  @PostMapping("/lines/{lineId}/quantity-policy")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "recordQuoteQuantityPolicy",
      summary = "Record a traced exact or agreed quantity policy on a draft quote line")
  public ResponseEntity<ApiResponse<QuoteRollSelectionDto>> policy(
      @PathVariable UUID quoteId,
      @PathVariable UUID lineId,
      @Valid @RequestBody QuoteRollSelectionRequests.Policy request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            service.policy(quoteId, lineId, request, TenantContext.getCurrentUserId())));
  }
}
