package com.fabricmanagement.sales.quote.dto;

import com.fabricmanagement.sales.quote.domain.QuoteApprovalChannel;
import com.fabricmanagement.sales.quote.domain.QuoteSendRequest;
import com.fabricmanagement.sales.quote.domain.QuoteSendRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Internal approval request for sending a quote to a customer contact")
public record QuoteSendRequestDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID quoteId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID contactId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) QuoteApprovalChannel channel,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) QuoteSendRequestStatus status,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID requestedBy,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant requestedAt,
    UUID decidedBy,
    Instant decidedAt,
    String decisionNote) {

  public static QuoteSendRequestDto from(QuoteSendRequest request) {
    return new QuoteSendRequestDto(
        request.getId(),
        request.getQuoteId(),
        request.getContactId(),
        request.getChannel(),
        request.getStatus(),
        request.getRequestedBy(),
        request.getRequestedAt(),
        request.getDecidedBy(),
        request.getDecidedAt(),
        request.getDecisionNote());
  }
}
