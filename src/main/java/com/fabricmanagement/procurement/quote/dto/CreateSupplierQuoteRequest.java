package com.fabricmanagement.procurement.quote.dto;

import com.fabricmanagement.procurement.quote.domain.QuoteEntryMethod;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Data;

@Data
public class CreateSupplierQuoteRequest {

  @NotNull(message = "RFQ ID is required")
  private UUID rfqId;

  @NotNull(message = "Trading partner ID is required")
  private UUID tradingPartnerId;

  @NotNull(message = "Valid-until date is required")
  @FutureOrPresent(message = "Valid-until date must be today or in the future")
  private LocalDate validUntil;

  @NotBlank(message = "Currency is required")
  private String currency;

  private String paymentTerms;

  private Integer leadTimeDays;

  @NotNull(message = "Entry method is required")
  private QuoteEntryMethod entryMethod;

  private String notes;
}
