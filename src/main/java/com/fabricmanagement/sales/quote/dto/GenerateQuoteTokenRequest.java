package com.fabricmanagement.sales.quote.dto;

import com.fabricmanagement.sales.quote.domain.QuoteApprovalChannel;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateQuoteTokenRequest {

  @NotNull(message = "Channel is required")
  private QuoteApprovalChannel channel;

  private String sentTo; // Optional, might be filled via UI (email or phone)
}
