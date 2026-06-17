package com.fabricmanagement.analytics.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;

@Builder
public record BacklogByCustomerDto(
    UUID customerId, String customerName, BigDecimal committedOrderValue, int orderCount) {}
