package com.fabricmanagement.sales.quote.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.quote.app.QuoteApprovalService;
import com.fabricmanagement.sales.quote.dto.CustomerApprovalRequest;
import com.fabricmanagement.sales.quote.dto.PublicQuoteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/sales/quotes")
@RequiredArgsConstructor
@Tag(name = "Public Quotes", description = "Customer-facing quote approval")
public class PublicQuoteController {

  private final QuoteApprovalService quoteApprovalService;

  @GetMapping("/by-token/{token}")
  @Operation(summary = "Get a public quote projection by approval token")
  public ResponseEntity<ApiResponse<PublicQuoteResponse>> getByToken(@PathVariable String token) {
    return ResponseEntity.ok(
        ApiResponse.success(quoteApprovalService.getPublicQuoteByToken(token)));
  }

  @PostMapping("/approve")
  @Operation(summary = "Customer approves a quote via approval token")
  public ResponseEntity<ApiResponse<PublicQuoteResponse>> approve(
      @Valid @RequestBody CustomerApprovalRequest req) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PublicQuoteResponse.from(
                quoteApprovalService.processCustomerApproval(
                    req.getToken(),
                    req.getIpAddress(),
                    req.getUserAgent(),
                    req.getCustomerNote()))));
  }
}
