package com.fabricmanagement.procurement.quote.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.procurement.quote.app.SupplierQuoteService;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteStatus;
import com.fabricmanagement.procurement.quote.dto.AddQuoteLineRequest;
import com.fabricmanagement.procurement.quote.dto.CreateSupplierQuoteRequest;
import com.fabricmanagement.procurement.quote.dto.SupplierQuoteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/procurement/supplier-quotes")
@RequiredArgsConstructor
@Tag(name = "Supplier Quote", description = "Supplier Quote Management API")
public class SupplierQuoteController {

  private final SupplierQuoteService quoteService;

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  @Operation(summary = "Get supplier quote by ID")
  public ResponseEntity<ApiResponse<SupplierQuoteResponse>> getQuote(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(quoteService.getQuoteById(id)));
  }

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  @Operation(summary = "List supplier quotes with pagination and filters")
  public ResponseEntity<ApiResponse<PagedResponse<SupplierQuoteResponse>>> listQuotes(
      @RequestParam(required = false) SupplierQuoteStatus status,
      @RequestParam(required = false) UUID rfqId,
      @RequestParam(required = false) UUID tradingPartnerId,
      Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(quoteService.listQuotes(status, rfqId, tradingPartnerId, pageable)));
  }

  @GetMapping("/by-rfq/{rfqId}")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  @Operation(summary = "List all quotes for a specific RFQ")
  public ResponseEntity<ApiResponse<List<SupplierQuoteResponse>>> getQuotesByRfq(
      @PathVariable UUID rfqId) {
    return ResponseEntity.ok(ApiResponse.success(quoteService.getQuotesByRfq(rfqId)));
  }

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(summary = "Create a new supplier quote")
  public ResponseEntity<ApiResponse<SupplierQuoteResponse>> createQuote(
      @Valid @RequestBody CreateSupplierQuoteRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                quoteService.createQuote(req), "Supplier Quote created successfully"));
  }

  @PostMapping("/{quoteId}/lines")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(summary = "Add a line to a supplier quote")
  public ResponseEntity<ApiResponse<SupplierQuoteResponse>> addLine(
      @PathVariable UUID quoteId, @Valid @RequestBody AddQuoteLineRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                quoteService.addLine(quoteId, req), "Quote line added successfully"));
  }

  @PostMapping("/{quoteId}/review")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(summary = "Start review process for a supplier quote")
  public ResponseEntity<ApiResponse<SupplierQuoteResponse>> startReview(
      @PathVariable UUID quoteId) {
    return ResponseEntity.ok(
        ApiResponse.success(quoteService.startReview(quoteId), "Quote review started"));
  }

  @PostMapping("/{quoteId}/accept")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(summary = "Accept a supplier quote and auto-reject siblings")
  public ResponseEntity<ApiResponse<SupplierQuoteResponse>> acceptQuote(
      @PathVariable UUID quoteId) {
    return ResponseEntity.ok(
        ApiResponse.success(quoteService.markAsAccepted(quoteId), "Quote accepted successfully"));
  }

  @PostMapping("/{quoteId}/reject")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(summary = "Reject a supplier quote")
  public ResponseEntity<ApiResponse<SupplierQuoteResponse>> rejectQuote(
      @PathVariable UUID quoteId) {
    return ResponseEntity.ok(
        ApiResponse.success(quoteService.markAsRejected(quoteId), "Quote rejected successfully"));
  }
}
