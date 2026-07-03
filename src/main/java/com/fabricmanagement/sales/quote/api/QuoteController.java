package com.fabricmanagement.sales.quote.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.sales.quote.app.QuoteApprovalService;
import com.fabricmanagement.sales.quote.app.QuoteService;
import com.fabricmanagement.sales.quote.dto.AddQuoteLineRequest;
import com.fabricmanagement.sales.quote.dto.CustomerApprovalRequest;
import com.fabricmanagement.sales.quote.dto.GenerateQuoteTokenRequest;
import com.fabricmanagement.sales.quote.dto.QuoteApprovalTokenDto;
import com.fabricmanagement.sales.quote.dto.QuoteResponse;
import com.fabricmanagement.sales.quote.dto.UpdateQuoteLineRequest;
import com.fabricmanagement.sales.quote.dto.UpdateQuoteRequest;
import com.fabricmanagement.sales.quote.mapper.QuoteMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales/quotes")
@RequiredArgsConstructor
@Tag(name = "Quotes", description = "Quote management with pricing engine integration")
public class QuoteController {

  private final QuoteService quoteService;
  private final QuoteApprovalService quoteApprovalService;
  private final QuoteMapper mapper;

  // ═══════════════════════════════════════════════════════════════════════════
  // READ
  // ═══════════════════════════════════════════════════════════════════════════

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "List all quotes (paginated)")
  public ResponseEntity<ApiResponse<PagedResponse<QuoteResponse>>> listQuotes(
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    Page<QuoteResponse> page = quoteService.findAll(pageable).map(QuoteResponse::from);
    return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(page)));
  }

  @GetMapping("/{quoteId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "Get a quote by ID")
  public ResponseEntity<ApiResponse<QuoteResponse>> getQuote(@PathVariable UUID quoteId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            QuoteResponse.from(
                quoteService
                    .findById(quoteId)
                    .orElseThrow(
                        () -> new EntityNotFoundException("Quote not found: " + quoteId)))));
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // WRITE
  // ═══════════════════════════════════════════════════════════════════════════

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Create a new quote")
  public ResponseEntity<ApiResponse<QuoteResponse>> createQuote(
      @Valid @RequestBody QuoteCreateRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(QuoteResponse.from(quoteService.createQuote(req))));
  }

  @PostMapping("/{quoteId}/lines")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Add a line item to a quote")
  public ResponseEntity<ApiResponse<QuoteResponse>> addLine(
      @PathVariable UUID quoteId, @Valid @RequestBody AddQuoteLineRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                QuoteResponse.from(
                    quoteService.addQuoteLine(
                        quoteId,
                        req.getProductId(),
                        req.getRequestedQty(),
                        req.getUnit(),
                        req.getOfferedPrice()))));
  }

  @PatchMapping("/{quoteId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Update editable quote header fields")
  public ResponseEntity<ApiResponse<QuoteResponse>> updateQuote(
      @PathVariable UUID quoteId, @Valid @RequestBody UpdateQuoteRequest req) {
    return ResponseEntity.ok(
        ApiResponse.success(QuoteResponse.from(quoteService.updateQuoteHeader(quoteId, req))));
  }

  @PatchMapping("/{quoteId}/lines/{lineId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Update a quote line and re-evaluate pricing")
  public ResponseEntity<ApiResponse<QuoteResponse>> updateLine(
      @PathVariable UUID quoteId,
      @PathVariable UUID lineId,
      @Valid @RequestBody UpdateQuoteLineRequest req) {
    return ResponseEntity.ok(
        ApiResponse.success(
            QuoteResponse.from(quoteService.updateQuoteLine(quoteId, lineId, req))));
  }

  @DeleteMapping("/{quoteId}/lines/{lineId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Remove a line item from a quote")
  public ResponseEntity<ApiResponse<QuoteResponse>> removeLine(
      @PathVariable UUID quoteId, @PathVariable UUID lineId) {
    return ResponseEntity.ok(
        ApiResponse.success(QuoteResponse.from(quoteService.removeQuoteLine(quoteId, lineId))));
  }

  @PostMapping("/{quoteId}/submit")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Submit a quote for approval")
  public ResponseEntity<ApiResponse<QuoteResponse>> submitQuote(@PathVariable UUID quoteId) {
    return ResponseEntity.ok(
        ApiResponse.success(QuoteResponse.from(quoteService.submitQuote(quoteId))));
  }

  @PostMapping("/{quoteId}/revise")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Create a new revision of a quote")
  public ResponseEntity<ApiResponse<QuoteResponse>> reviseQuote(@PathVariable UUID quoteId) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(QuoteResponse.from(quoteService.reviseQuote(quoteId))));
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // APPROVAL TOKENS
  // ═══════════════════════════════════════════════════════════════════════════

  @PostMapping("/{quoteId}/tokens")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Generate an approval token for a quote")
  public ResponseEntity<ApiResponse<QuoteApprovalTokenDto>> generateToken(
      @PathVariable UUID quoteId, @Valid @RequestBody GenerateQuoteTokenRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                mapper.toDto(
                    quoteApprovalService.generateTokenForQuote(
                        quoteId, req.getChannel(), req.getSentTo()))));
  }

  /** Public endpoint intended for the customer-facing frontend. */
  @PostMapping("/public/approve")
  @Operation(summary = "Customer approves a quote via token (public endpoint)")
  public ResponseEntity<ApiResponse<QuoteResponse>> customerApprove(
      @Valid @RequestBody CustomerApprovalRequest req) {
    return ResponseEntity.ok(
        ApiResponse.success(
            QuoteResponse.from(
                quoteApprovalService.processCustomerApproval(
                    req.getToken(), req.getIpAddress(), req.getUserAgent()))));
  }
}
