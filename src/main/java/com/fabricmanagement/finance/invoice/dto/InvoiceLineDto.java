package com.fabricmanagement.finance.invoice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceLineDto(
    UUID id,
    String uid,
    Integer lineNumber,
    String description,
    String productCode,
    String unit,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal discountRate,
    BigDecimal taxRate,
    BigDecimal lineSubtotal,
    BigDecimal lineTax,
    BigDecimal lineDiscount,
    BigDecimal lineTotal,
    String notes) {}
