package com.fabricmanagement.sales.quote.dto;

import com.fabricmanagement.sales.quote.domain.QuoteApprovalChannel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GenerateQuoteTokenRequest {

  @NotNull(message = "Channel is required")
  private QuoteApprovalChannel channel;

  @Size(max = 254, message = "Recipient must be 254 characters or less")
  private String sentTo; // Optional, might be filled via UI (email or phone)
}
