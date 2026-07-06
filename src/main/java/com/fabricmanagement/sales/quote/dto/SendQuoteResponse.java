package com.fabricmanagement.sales.quote.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of a quote send attempt")
public record SendQuoteResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SendQuoteResult result,
    QuoteApprovalTokenDto approvalToken,
    QuoteSendRequestDto sendRequest) {

  public static SendQuoteResponse sent(QuoteApprovalTokenDto approvalToken) {
    return new SendQuoteResponse(SendQuoteResult.SENT, approvalToken, null);
  }

  public static SendQuoteResponse sent(
      QuoteApprovalTokenDto approvalToken, QuoteSendRequestDto sendRequest) {
    return new SendQuoteResponse(SendQuoteResult.SENT, approvalToken, sendRequest);
  }

  public static SendQuoteResponse awaitingApproval(QuoteSendRequestDto sendRequest) {
    return new SendQuoteResponse(SendQuoteResult.AWAITING_APPROVAL, null, sendRequest);
  }

  public enum SendQuoteResult {
    SENT,
    AWAITING_APPROVAL
  }
}
