package com.fabricmanagement.sales.quote.app;

import com.fabricmanagement.sales.quote.domain.QuoteApprovalToken;
import com.fabricmanagement.sales.quote.domain.QuoteSendRequest;

public record SendQuoteResult(QuoteApprovalToken approvalToken, QuoteSendRequest sendRequest) {

  public boolean awaitingApproval() {
    return sendRequest != null && approvalToken == null;
  }

  public static SendQuoteResult sent(QuoteApprovalToken approvalToken) {
    return new SendQuoteResult(approvalToken, null);
  }

  public static SendQuoteResult sent(QuoteApprovalToken approvalToken, QuoteSendRequest request) {
    return new SendQuoteResult(approvalToken, request);
  }

  public static SendQuoteResult awaitingApproval(QuoteSendRequest request) {
    return new SendQuoteResult(null, request);
  }
}
