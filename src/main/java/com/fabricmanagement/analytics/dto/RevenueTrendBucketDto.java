package com.fabricmanagement.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

@Builder
public record RevenueTrendBucketDto(
    String key, // e.g., "2024-06"
    String label, // e.g., "Jun 2024"
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal totalAmount,
    List<RevenueTrendCustomerDto> byCustomer) {}
