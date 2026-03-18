package com.fabricmanagement.sales.api;

import com.fabricmanagement.sales.domain.quote.Quote;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Data;

/**
 * Request body for creating a new Quote. Keeps API surface clean — internal fields (tenantId,
 * revisionNumber, status) are set by the service layer.
 */
@Data
public class QuoteCreateRequest {

  @NotNull(message = "Customer ID is required")
  private UUID customerId;

  @NotNull(message = "Assigned salesperson ID is required")
  private UUID assignedToId;

  @NotBlank(message = "Module type is required")
  private String moduleType;

  @NotBlank(message = "Quote number is required")
  private String quoteNumber;

  /**
   * Estimated unit cost pulled from Phase 4 CostCalculation. Optional at draft creation — can be
   * added later by linking to a CostCalculation record.
   */
  private BigDecimal estimatedUnitCost;

  @NotNull(message = "Valid-until date is required")
  @FutureOrPresent(message = "Valid-until date must be today or in the future")
  private LocalDate validUntil;

  private String paymentTerms;

  private Integer leadTimeDays;

  private String notes;

  /** Populated only when quote was created offline on a mobile device. */
  private String deviceId;

  /** Maps this request to a transient Quote entity for service layer processing. */
  public Quote toQuote() {
    Quote q = new Quote();
    q.setCustomerId(customerId);
    q.setAssignedToId(assignedToId);
    q.setModuleType(moduleType);
    q.setQuoteNumber(quoteNumber);
    q.setEstimatedUnitCost(estimatedUnitCost);
    q.setValidUntil(validUntil);
    q.setPaymentTerms(paymentTerms);
    q.setLeadTimeDays(leadTimeDays);
    q.setNotes(notes);
    q.setDeviceId(deviceId);
    return q;
  }
}
