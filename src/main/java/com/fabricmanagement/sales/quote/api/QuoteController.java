package com.fabricmanagement.sales.quote.api;

import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.sales.quote.app.QuoteApprovalService;
import com.fabricmanagement.sales.quote.app.QuoteService;
import com.fabricmanagement.sales.quote.app.SendQuoteResult;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import com.fabricmanagement.sales.quote.dto.AddQuoteLineRequest;
import com.fabricmanagement.sales.quote.dto.GenerateQuoteTokenRequest;
import com.fabricmanagement.sales.quote.dto.QuoteApprovalTokenDto;
import com.fabricmanagement.sales.quote.dto.QuoteResponse;
import com.fabricmanagement.sales.quote.dto.QuoteSendRequestDto;
import com.fabricmanagement.sales.quote.dto.QuoteStatusCountsResponse;
import com.fabricmanagement.sales.quote.dto.RejectQuoteSendRequest;
import com.fabricmanagement.sales.quote.dto.SendQuoteRequest;
import com.fabricmanagement.sales.quote.dto.SendQuoteResponse;
import com.fabricmanagement.sales.quote.dto.UpdateQuoteLineRequest;
import com.fabricmanagement.sales.quote.dto.UpdateQuoteRequest;
import com.fabricmanagement.sales.quote.mapper.QuoteMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales/quotes")
@RequiredArgsConstructor
@Tag(name = "Quotes", description = "Quote management with pricing engine integration")
public class QuoteController {

  private final QuoteService quoteService;
  private final QuoteApprovalService quoteApprovalService;
  private final QuoteMapper mapper;
  private final SpELPermissionEvaluator auth;

  // ═══════════════════════════════════════════════════════════════════════════
  // READ
  // ═══════════════════════════════════════════════════════════════════════════

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "List all quotes (paginated)")
  public ResponseEntity<ApiResponse<PagedResponse<QuoteResponse>>> listQuotes(
      @Parameter(description = "Optional quote status filter") @RequestParam(required = false)
          QuoteStatus status,
      @Parameter(
              description =
                  "Literal quote-number or customer-name search; trimmed values shorter than two characters are ignored")
          @RequestParam(required = false)
          String q,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(quoteService.findAllResponses(status, q, pageable))));
  }

  @GetMapping("/status-counts")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "Count active quotes by status")
  public ResponseEntity<ApiResponse<QuoteStatusCountsResponse>> getStatusCounts() {
    return ResponseEntity.ok(ApiResponse.success(quoteService.getStatusCounts()));
  }

  @GetMapping("/{quoteId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "Get a quote by ID")
  public ResponseEntity<ApiResponse<QuoteResponse>> getQuote(@PathVariable UUID quoteId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            quoteService
                .findResponseById(quoteId)
                .orElseThrow(() -> new EntityNotFoundException("Quote not found: " + quoteId))));
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
        .body(ApiResponse.success(quoteService.toResponse(quoteService.createQuote(req))));
  }

  @PostMapping("/{quoteId}/lines")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Add a line item to a quote")
  public ResponseEntity<ApiResponse<QuoteResponse>> addLine(
      @PathVariable UUID quoteId, @Valid @RequestBody AddQuoteLineRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(quoteService.toResponse(quoteService.addQuoteLine(quoteId, req))));
  }

  @PatchMapping("/{quoteId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Update editable quote header fields")
  public ResponseEntity<ApiResponse<QuoteResponse>> updateQuote(
      @PathVariable UUID quoteId, @Valid @RequestBody UpdateQuoteRequest req) {
    return ResponseEntity.ok(
        ApiResponse.success(quoteService.toResponse(quoteService.updateQuoteHeader(quoteId, req))));
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
            quoteService.toResponse(quoteService.updateQuoteLine(quoteId, lineId, req))));
  }

  @DeleteMapping("/{quoteId}/lines/{lineId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Remove a line item from a quote")
  public ResponseEntity<ApiResponse<QuoteResponse>> removeLine(
      @PathVariable UUID quoteId, @PathVariable UUID lineId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            quoteService.toResponse(quoteService.removeQuoteLine(quoteId, lineId))));
  }

  @PostMapping("/{quoteId}/submit")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Submit a quote for approval")
  public ResponseEntity<ApiResponse<QuoteResponse>> submitQuote(@PathVariable UUID quoteId) {
    return ResponseEntity.ok(
        ApiResponse.success(quoteService.toResponse(quoteService.submitQuote(quoteId))));
  }

  @PostMapping("/{quoteId}/send")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Send a quote to a customer or request internal send approval")
  public ResponseEntity<ApiResponse<SendQuoteResponse>> sendQuote(
      @PathVariable UUID quoteId,
      @Valid @RequestBody SendQuoteRequest req,
      Authentication authentication) {
    boolean callerCanApprove = auth.can(authentication, "sales", "approve");
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                toSendQuoteResponse(
                    quoteService.sendQuote(quoteId, req.getContactId(), callerCanApprove))));
  }

  @PostMapping("/{quoteId}/send-requests/{requestId}/approve")
  @PreAuthorize("@auth.can(authentication, 'sales', 'approve')")
  @Operation(summary = "Approve a quote send request and send the quote to the customer")
  public ResponseEntity<ApiResponse<SendQuoteResponse>> approveSendRequest(
      @PathVariable UUID quoteId, @PathVariable UUID requestId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            toSendQuoteResponse(quoteService.approveSendRequest(quoteId, requestId))));
  }

  @PostMapping("/{quoteId}/send-requests/{requestId}/reject")
  @PreAuthorize("@auth.can(authentication, 'sales', 'approve')")
  @Operation(summary = "Reject a quote send request")
  public ResponseEntity<ApiResponse<QuoteSendRequestDto>> rejectSendRequest(
      @PathVariable UUID quoteId,
      @PathVariable UUID requestId,
      @Valid @RequestBody RejectQuoteSendRequest req) {
    return ResponseEntity.ok(
        ApiResponse.success(
            QuoteSendRequestDto.from(
                quoteService.rejectSendRequest(quoteId, requestId, req.decisionNote()))));
  }

  @PostMapping("/{quoteId}/revise")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Create a new revision of a quote")
  public ResponseEntity<ApiResponse<QuoteResponse>> reviseQuote(@PathVariable UUID quoteId) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(quoteService.toResponse(quoteService.reviseQuote(quoteId))));
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

  private SendQuoteResponse toSendQuoteResponse(SendQuoteResult result) {
    QuoteApprovalTokenDto token =
        result.approvalToken() != null ? mapper.toDto(result.approvalToken()) : null;
    QuoteSendRequestDto request =
        result.sendRequest() != null ? QuoteSendRequestDto.from(result.sendRequest()) : null;
    if (result.awaitingApproval()) {
      return SendQuoteResponse.awaitingApproval(request);
    }
    return request != null ? SendQuoteResponse.sent(token, request) : SendQuoteResponse.sent(token);
  }
}
