package com.fabricmanagement.finance.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PaymentDto(
    UUID id,
    UUID tradingPartnerId,
    String paymentNumber,
    String direction,
    String method,
    String status,
    BigDecimal amount,
    String currency,
    BigDecimal allocatedAmount,
    BigDecimal unallocatedAmount,
    LocalDate paymentDate,
    String bankReference,
    String notes,
    Map<String, Object> metadata,
    List<PaymentAllocationDto> allocations,
    Instant createdAt,
    Instant updatedAt) {}
