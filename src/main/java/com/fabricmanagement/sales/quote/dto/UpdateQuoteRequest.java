package com.fabricmanagement.sales.quote.dto;

import jakarta.validation.constraints.FutureOrPresent;
import java.time.LocalDate;
import lombok.Data;

@Data
public class UpdateQuoteRequest {

  @FutureOrPresent(message = "Valid-until date must be today or in the future")
  private LocalDate validUntil;

  private String paymentTerms;

  private Integer leadTimeDays;

  private String notes;
}
