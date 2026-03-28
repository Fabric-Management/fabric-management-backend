package com.fabricmanagement.finance.invoice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RecordPaymentRequest(@NotNull @DecimalMin("0.01") BigDecimal amount) {}
