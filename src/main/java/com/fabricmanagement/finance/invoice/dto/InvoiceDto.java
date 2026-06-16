package com.fabricmanagement.finance.invoice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceDto(
    UUID id,
    String uid,
    UUID tradingPartnerId,
    String tradingPartnerName,
    String invoiceNumber,
    String orderReference,
    String externalReference,
    String invoiceType,
    String status,
    String paymentStatus,
    UUID originalInvoiceId,
    LocalDate issueDate,
    LocalDate dueDate,
    LocalDate paymentDate,
    BigDecimal subtotal,
    BigDecimal taxAmount,
    BigDecimal discountAmount,
    BigDecimal totalAmount,
    BigDecimal amountPaid,
    BigDecimal amountCredited,
    BigDecimal amountDue,
    String currency,
    String reportingCurrency,
    BigDecimal issueExchangeRate,
    LocalDate issueExchangeRateDate,
    BigDecimal reportingTotal,
    BigDecimal taxRate,
    String billingAddress,
    String notes,
    List<InvoiceLineDto> lines,
    List<InvoiceTaxLineDto> taxBreakdown,
    long daysOverdue,
    boolean overdue) {}
