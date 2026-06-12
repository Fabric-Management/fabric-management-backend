package com.fabricmanagement.finance.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreatePaymentRequest(
    @NotNull UUID tradingPartnerId,
    @NotBlank String direction,
    @NotBlank String method,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotBlank String currency,
    @NotNull LocalDate paymentDate,
    String bankReference,
    String notes,
    @Valid List<CreateAllocationRequest> allocations) {}
