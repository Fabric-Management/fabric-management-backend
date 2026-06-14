package com.fabricmanagement.finance.invoice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateCreditNoteApplicationRequest(
    @NotNull UUID targetInvoiceId, @NotNull @Positive BigDecimal amount) {}
