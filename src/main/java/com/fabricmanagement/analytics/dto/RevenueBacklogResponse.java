package com.fabricmanagement.analytics.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record RevenueBacklogResponse(
    List<RevenueTrendBucketDto> revenueTrend,
    List<BacklogByCustomerDto> backlogByCustomer,
    String reportingCurrency,
    List<RevenueBacklogWarningDto> warnings) {}
