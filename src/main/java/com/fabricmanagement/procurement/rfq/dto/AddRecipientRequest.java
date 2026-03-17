package com.fabricmanagement.procurement.rfq.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/** Fix #10 — @RequestParam yerine JSON body DTO. */
@Data
public class AddRecipientRequest {

  @NotNull(message = "Trading partner ID is required")
  private UUID tradingPartnerId;

  /** Tedarikçiye özgü son tarih. Null ise RFQ.deadline kullanılır. */
  private Instant responseDeadline;
}
