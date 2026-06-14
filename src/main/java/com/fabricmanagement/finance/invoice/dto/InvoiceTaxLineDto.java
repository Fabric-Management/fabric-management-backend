package com.fabricmanagement.finance.invoice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(
    name = "InvoiceTaxLineDto",
    requiredProperties = {"taxCategory", "taxRate", "taxableBase", "taxAmount"})
public record InvoiceTaxLineDto(
    String taxCategory, BigDecimal taxRate, BigDecimal taxableBase, BigDecimal taxAmount) {}
