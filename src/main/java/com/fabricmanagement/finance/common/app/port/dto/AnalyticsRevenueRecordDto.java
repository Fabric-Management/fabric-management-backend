package com.fabricmanagement.finance.common.app.port.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record AnalyticsRevenueRecordDto(
    UUID customerId, LocalDate issueDate, BigDecimal reportingAmount) {}
