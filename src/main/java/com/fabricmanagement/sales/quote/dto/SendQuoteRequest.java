package com.fabricmanagement.sales.quote.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class SendQuoteRequest {

  @NotNull(message = "Contact ID is required")
  private UUID contactId;
}
