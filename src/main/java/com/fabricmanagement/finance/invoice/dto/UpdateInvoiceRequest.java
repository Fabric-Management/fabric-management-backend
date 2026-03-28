package com.fabricmanagement.finance.invoice.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateInvoiceRequest(
    String orderReference,
    String externalReference,
    LocalDate issueDate,
    LocalDate dueDate,
    @DecimalMin("0") BigDecimal subtotal,
    @DecimalMin("0") BigDecimal taxAmount,
    @DecimalMin("0") BigDecimal discountAmount,
    @DecimalMin("0") BigDecimal totalAmount,
    BigDecimal taxRate,
    String billingAddress,
    String notes) {}
