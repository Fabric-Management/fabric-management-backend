package com.fabricmanagement.sales.quote.api;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
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
import org.springframework.security.access.AccessDeniedException;
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
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
      Authentication authentication) {
    var page = quoteService.findAllResponses(status, q, pageable, currentUserId(authentication));
    return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(page)));
  }

  @GetMapping("/status-counts")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "Count active quotes by status")
  public ResponseEntity<ApiResponse<QuoteStatusCountsResponse>> getStatusCounts(
      Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(quoteService.getStatusCounts(currentUserId(authentication))));
  }

  @GetMapping("/{quoteId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "Get a quote by ID")
  public ResponseEntity<ApiResponse<QuoteResponse>> getQuote(
      @PathVariable UUID quoteId, Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(
            quoteService
                .findResponseById(quoteId, currentUserId(authentication))
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
      @PathVariable UUID quoteId,
      @Valid @RequestBody AddQuoteLineRequest req,
      Authentication authentication) {
    QuoteResponse response =
        quoteService.toResponse(
            quoteService.addQuoteLine(quoteId, req, currentUserId(authentication)));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @PatchMapping("/{quoteId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Update editable quote header fields")
  public ResponseEntity<ApiResponse<QuoteResponse>> updateQuote(
      @PathVariable UUID quoteId,
      @Valid @RequestBody UpdateQuoteRequest req,
      Authentication authentication) {
    QuoteResponse response =
        quoteService.toResponse(
            quoteService.updateQuoteHeader(quoteId, req, currentUserId(authentication)));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PatchMapping("/{quoteId}/lines/{lineId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Update a quote line and re-evaluate pricing")
  public ResponseEntity<ApiResponse<QuoteResponse>> updateLine(
      @PathVariable UUID quoteId,
      @PathVariable UUID lineId,
      @Valid @RequestBody UpdateQuoteLineRequest req,
      Authentication authentication) {
    QuoteResponse response =
        quoteService.toResponse(
            quoteService.updateQuoteLine(quoteId, lineId, req, currentUserId(authentication)));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @DeleteMapping("/{quoteId}/lines/{lineId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Remove a line item from a quote")
  public ResponseEntity<ApiResponse<QuoteResponse>> removeLine(
      @PathVariable UUID quoteId, @PathVariable UUID lineId, Authentication authentication) {
    QuoteResponse response =
        quoteService.toResponse(
            quoteService.removeQuoteLine(quoteId, lineId, currentUserId(authentication)));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/{quoteId}/submit")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Submit a quote for approval")
  public ResponseEntity<ApiResponse<QuoteResponse>> submitQuote(
      @PathVariable UUID quoteId, Authentication authentication) {
    QuoteResponse response =
        quoteService.toResponse(quoteService.submitQuote(quoteId, currentUserId(authentication)));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/{quoteId}/send")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Send a quote to a customer or request internal send approval")
  public ResponseEntity<ApiResponse<SendQuoteResponse>> sendQuote(
      @PathVariable UUID quoteId,
      @Valid @RequestBody SendQuoteRequest req,
      Authentication authentication) {
    boolean callerCanApprove = auth.can(authentication, "sales", "approve");
    SendQuoteResponse response =
        toSendQuoteResponse(
            quoteService.sendQuote(
                quoteId, req.getContactId(), callerCanApprove, currentUserId(authentication)));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @PostMapping("/{quoteId}/send-requests/{requestId}/approve")
  @PreAuthorize("@auth.can(authentication, 'sales', 'approve')")
  @Operation(summary = "Approve a quote send request and send the quote to the customer")
  public ResponseEntity<ApiResponse<SendQuoteResponse>> approveSendRequest(
      @PathVariable UUID quoteId, @PathVariable UUID requestId, Authentication authentication) {
    SendQuoteResponse response =
        toSendQuoteResponse(
            quoteService.approveSendRequest(quoteId, requestId, currentUserId(authentication)));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/{quoteId}/send-requests/{requestId}/reject")
  @PreAuthorize("@auth.can(authentication, 'sales', 'approve')")
  @Operation(summary = "Reject a quote send request")
  public ResponseEntity<ApiResponse<QuoteSendRequestDto>> rejectSendRequest(
      @PathVariable UUID quoteId,
      @PathVariable UUID requestId,
      @Valid @RequestBody RejectQuoteSendRequest req,
      Authentication authentication) {
    QuoteSendRequestDto response =
        QuoteSendRequestDto.from(
            quoteService.rejectSendRequest(
                quoteId, requestId, req.decisionNote(), currentUserId(authentication)));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/{quoteId}/revise")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Create a new revision of a quote")
  public ResponseEntity<ApiResponse<QuoteResponse>> reviseQuote(
      @PathVariable UUID quoteId, Authentication authentication) {
    QuoteResponse response =
        quoteService.toResponse(quoteService.reviseQuote(quoteId, currentUserId(authentication)));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // APPROVAL TOKENS
  // ═══════════════════════════════════════════════════════════════════════════

  @PostMapping("/{quoteId}/tokens")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Generate an approval token for a quote")
  public ResponseEntity<ApiResponse<QuoteApprovalTokenDto>> generateToken(
      @PathVariable UUID quoteId,
      @Valid @RequestBody GenerateQuoteTokenRequest req,
      Authentication authentication) {
    QuoteApprovalTokenDto response =
        mapper.toDto(
            quoteService.generateTokenForQuote(
                quoteId, req.getChannel(), req.getSentTo(), currentUserId(authentication)));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  private UUID currentUserId(Authentication authentication) {
    if (authentication != null
        && authentication.getPrincipal() instanceof AuthenticatedUserContext context) {
      return context.userId();
    }
    throw new AccessDeniedException("Authenticated user context is required.");
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
