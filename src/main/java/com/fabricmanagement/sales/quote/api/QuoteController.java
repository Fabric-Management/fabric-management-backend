package com.fabricmanagement.sales.quote.api;

import com.fabricmanagement.sales.quote.api.dto.AddQuoteLineRequest;
import com.fabricmanagement.sales.quote.api.dto.CustomerApprovalRequest;
import com.fabricmanagement.sales.quote.api.dto.GenerateQuoteTokenRequest;
import com.fabricmanagement.sales.quote.api.dto.QuoteResponse;
import com.fabricmanagement.sales.quote.app.QuoteApprovalService;
import com.fabricmanagement.sales.quote.app.QuoteService;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalToken;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales/quotes")
@RequiredArgsConstructor
public class QuoteController {

  private final QuoteService quoteService;
  private final QuoteApprovalService quoteApprovalService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public QuoteResponse createQuote(@Valid @RequestBody QuoteCreateRequest req) {
    return QuoteResponse.from(quoteService.createQuote(req));
  }

  @PostMapping("/{quoteId}/lines")
  @ResponseStatus(HttpStatus.CREATED)
  public QuoteResponse addLine(
      @PathVariable UUID quoteId, @Valid @RequestBody AddQuoteLineRequest req) {
    return QuoteResponse.from(
        quoteService.addQuoteLine(
            quoteId,
            req.getMaterialId(),
            req.getRequestedQty(),
            req.getUnit(),
            req.getOfferedPrice()));
  }

  @PostMapping("/{quoteId}/submit")
  public QuoteResponse submitQuote(@PathVariable UUID quoteId) {
    return QuoteResponse.from(quoteService.submitQuote(quoteId));
  }

  @PostMapping("/{quoteId}/revise")
  @ResponseStatus(HttpStatus.CREATED)
  public QuoteResponse reviseQuote(@PathVariable UUID quoteId) {
    return QuoteResponse.from(quoteService.reviseQuote(quoteId));
  }

  @PostMapping("/{quoteId}/tokens")
  @ResponseStatus(HttpStatus.CREATED)
  public QuoteApprovalToken generateToken(
      @PathVariable UUID quoteId, @Valid @RequestBody GenerateQuoteTokenRequest req) {
    return quoteApprovalService.generateTokenForQuote(quoteId, req.getChannel(), req.getSentTo());
  }

  /** Public endpoint intended for the customer-facing frontend. */
  @PostMapping("/public/approve")
  public QuoteResponse customerApprove(@Valid @RequestBody CustomerApprovalRequest req) {
    return QuoteResponse.from(
        quoteApprovalService.processCustomerApproval(
            req.getToken(), req.getIpAddress(), req.getUserAgent()));
  }
}
