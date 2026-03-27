package com.fabricmanagement.finance.invoice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateInvoiceLineRequest(
    @NotBlank String description,
    String productCode,
    String unit,
    @NotNull @DecimalMin("0.0001") BigDecimal quantity,
    @NotNull @DecimalMin("0") BigDecimal unitPrice,
    BigDecimal discountRate,
    BigDecimal taxRate,
    String notes) {}
