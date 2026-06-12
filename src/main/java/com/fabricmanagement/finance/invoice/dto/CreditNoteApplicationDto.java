package com.fabricmanagement.finance.invoice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreditNoteApplicationDto(
    UUID id,
    UUID creditNoteId,
    UUID targetInvoiceId,
    BigDecimal amount,
    String currency,
    Instant appliedAt) {}
