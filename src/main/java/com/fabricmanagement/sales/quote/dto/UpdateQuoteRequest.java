package com.fabricmanagement.sales.quote.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Data;

@Data
public class UpdateQuoteRequest {

  private UUID customerId;

  @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
  private String currency;

  @FutureOrPresent(message = "Valid-until date must be today or in the future")
  private LocalDate validUntil;

  @Size(max = 50, message = "Payment terms must be 50 characters or less")
  private String paymentTerms;

  /**
   * @deprecated Superseded by per-line {@code QuoteLine.deliveryStatus}/{@code deliveryDate}
   *     (QLINE-ATP-1); retained for historical data, no longer written by the FE. See
   *     docs/sales/tickets/QLINE-LEADTIME-1-remove-quote-header-lead-time.md.
   */
  @Deprecated
  @Min(value = 0, message = "Lead time days must be zero or greater")
  @Max(value = 3650, message = "Lead time days must be 3650 or less")
  private Integer leadTimeDays;

  @Size(max = 2000, message = "Notes must be 2000 characters or less")
  private String notes;
}
