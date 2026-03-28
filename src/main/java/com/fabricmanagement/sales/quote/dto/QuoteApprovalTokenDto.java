package com.fabricmanagement.sales.quote.dto;

import com.fabricmanagement.sales.quote.domain.QuoteApprovalChannel;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Value;

@Value
public class QuoteApprovalTokenDto {
  UUID id;
  UUID quoteId;
  String token;
  QuoteApprovalChannel channel;
  String sentTo;
  Instant expiresAt;
  QuoteApprovalStatus status;
  Instant usedAt;
  String ipAddress;
  String userAgent;
  String location;
  boolean isActive;
}
