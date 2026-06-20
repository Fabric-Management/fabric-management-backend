package com.fabricmanagement.analytics.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;

@Builder
public record RevenueTrendCustomerDto(UUID customerId, String customerName, BigDecimal amount) {}
