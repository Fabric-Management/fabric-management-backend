package com.fabricmanagement.finance.invoice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateInvoiceRequest(
    @NotNull UUID tradingPartnerId,
    String orderReference,
    String externalReference,
    @NotNull String invoiceType,
    @NotNull LocalDate issueDate,
    @NotNull LocalDate dueDate,
    @NotNull @DecimalMin("0") BigDecimal subtotal,
    @DecimalMin("0") BigDecimal taxAmount,
    @DecimalMin("0") BigDecimal discountAmount,
    @NotNull @DecimalMin("0") BigDecimal totalAmount,
    String currency,
    BigDecimal taxRate,
    String billingAddress,
    String notes,
    UUID originalInvoiceId,
    @Valid List<CreateInvoiceLineRequest> lines) {}
